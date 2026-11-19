package com.muzrc.klipsync.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbMatrix
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.muzrc.klipsync.model.AspectRatioPreset
import com.muzrc.klipsync.model.AudioTrackConfig
import com.muzrc.klipsync.model.ExportConfig
import com.muzrc.klipsync.model.JoinMode
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.TransitionConfig
import com.muzrc.klipsync.model.TransitionType
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.model.VideoEndBehavior
import java.io.File
import java.io.FileOutputStream

@UnstableApi
class VideoSequenceBuilder(private val context: Context) {

    fun buildVideoSequence(
        clips: List<VideoClip>,
        transitionConfig: TransitionConfig,
        audioConfig: AudioTrackConfig,
        aspectRatio: AspectRatioPreset,
        subtitles: List<SubtitleItem>,
        exportConfig: ExportConfig,
        freezeFrameExtensionMs: Long,
        autoLoopExtensionMs: Long
    ): EditedMediaItemSequence? {
        if (clips.isEmpty()) return null

        val baseExpandedClips = clips.flatMap { clip ->
            List(clip.loopCount.coerceAtLeast(1)) { clip }
        }

        val finalClipChain = mutableListOf<VideoClip>()
        val targetMinDurationMs = if (
            transitionConfig.endBehavior == VideoEndBehavior.LOOP_TO_AUDIO &&
            autoLoopExtensionMs > 0L
        ) {
            audioConfig.effectiveAudioDurationMs
        } else {
            0L
        }

        var accumulatedMs = 0L
        var safetyIterations = 0
        do {
            for (clip in baseExpandedClips) {
                finalClipChain.add(clip)
                accumulatedMs += clip.singlePlayDurationMs
                if (targetMinDurationMs > 0L && accumulatedMs >= targetMinDurationMs) {
                    break
                }
            }
            safetyIterations++
        } while (targetMinDurationMs > 0L && accumulatedMs < targetMinDurationMs && safetyIterations < 100)

        val editedItems = mutableListOf<EditedMediaItem>()
        val baseVideoEffects = mutableListOf<Effect>()
        baseVideoEffects.addAll(
            CropEffectBuilder.buildCropAndScaleEffects(aspectRatio, exportConfig)
        )
        SubtitleOverlayBuilder.buildSubtitleEffect(subtitles)?.let {
            baseVideoEffects.add(it)
        }

        val hasFreezeFrame = transitionConfig.endBehavior == VideoEndBehavior.FREEZE_FRAME &&
            freezeFrameExtensionMs > 0L

        // Jika ada freeze frame (imej tanpa trek audio), pastikan trek video juga konsisten
        val removeOriginalAudio = audioConfig.isOriginalAudioMuted ||
            audioConfig.originalVideoVolume <= 0.01f

        val overlapHalfMs = if (transitionConfig.joinMode == JoinMode.OVERLAP && finalClipChain.size > 1) {
            (transitionConfig.durationMs / 2L).coerceAtLeast(0L)
        } else {
            0L
        }

        var runningDurationMs = 0L

        finalClipChain.forEachIndexed { index, clip ->
            val isFirst = index == 0
            val isLast = index == finalClipChain.lastIndex

            val adjustedStart = if (!isFirst) {
                (clip.trimStartMs + overlapHalfMs).coerceAtMost(clip.trimEndMs)
            } else {
                clip.trimStartMs
            }

            var adjustedEnd = if (!isLast) {
                (clip.trimEndMs - overlapHalfMs).coerceAtLeast(adjustedStart)
            } else {
                clip.trimEndMs
            }

            if (targetMinDurationMs > 0L && runningDurationMs + (adjustedEnd - adjustedStart) > targetMinDurationMs) {
                val needed = (targetMinDurationMs - runningDurationMs).coerceAtLeast(100L)
                adjustedEnd = (adjustedStart + needed).coerceAtMost(clip.trimEndMs)
            }

            val clipDurationMs = (adjustedEnd - adjustedStart).coerceAtLeast(100L)
            runningDurationMs += clipDurationMs

            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(adjustedStart)
                .setEndPositionMs(adjustedEnd)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(clipping)
                .build()

            val clipVideoEffects = mutableListOf<Effect>()
            clipVideoEffects.addAll(baseVideoEffects)

            if (finalClipChain.size > 1 && transitionConfig.transitionType != TransitionType.NONE) {
                val transDurationUs = (transitionConfig.durationMs.coerceAtMost(clipDurationMs / 2L)) * 1000L
                val clipDurationUs = clipDurationMs * 1000L

                when (transitionConfig.transitionType) {
                    TransitionType.CROSSFADE,
                    TransitionType.FADE_TO_BLACK -> {
                        if (!isFirst && transDurationUs > 0L) {
                            clipVideoEffects.add(
                                createFadeEffect(
                                    startUs = 0L,
                                    endUs = transDurationUs,
                                    fadeIn = true
                                )
                            )
                        }
                        if (!isLast && transDurationUs > 0L) {
                            clipVideoEffects.add(
                                createFadeEffect(
                                    startUs = (clipDurationUs - transDurationUs).coerceAtLeast(0L),
                                    endUs = clipDurationUs,
                                    fadeIn = false
                                )
                            )
                        }
                    }

                    TransitionType.SLIDE_LEFT -> {
                        if (!isFirst && transDurationUs > 0L) {
                            clipVideoEffects.add(createSlideInEffect(transDurationUs))
                        }
                    }

                    TransitionType.NONE -> Unit
                }
            }

            val audioProcessors = if (!removeOriginalAudio) {
                listOf(
                    AudioSequenceBuilder.createConstantVolumeProcessor(
                        audioConfig.originalVideoVolume
                    )
                )
            } else {
                emptyList()
            }

            val editedClip = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(removeOriginalAudio)
                .setFrameRate(exportConfig.frameRate.fps)
                .setEffects(Effects(audioProcessors, clipVideoEffects))
                .build()

            editedItems.add(editedClip)
        }

        // Freeze frame dengan MimeTypes.IMAGE_PNG supaya Media3 guna ImageAssetLoader
        if (hasFreezeFrame) {
            val lastClip = finalClipChain.last()
            val freezeUri = extractLastFrameToTempFile(lastClip)
            if (freezeUri != null) {
                val imageItem = MediaItem.Builder()
                    .setUri(freezeUri)
                    .setMimeType(MimeTypes.IMAGE_PNG)
                    .setImageDurationMs(freezeFrameExtensionMs)
                    .build()

                val freezeEditedItem = EditedMediaItem.Builder(imageItem)
                    .setRemoveAudio(true)
                    .setFrameRate(exportConfig.frameRate.fps)
                    .setEffects(Effects(emptyList(), baseVideoEffects))
                    .build()

                editedItems.add(freezeEditedItem)
            }
        }

        return EditedMediaItemSequence(editedItems)
    }

    private fun createFadeEffect(
        startUs: Long,
        endUs: Long,
        fadeIn: Boolean
    ): Effect {
        return object : RgbMatrix {
            override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
                val progress = if (endUs > startUs) {
                    ((presentationTimeUs - startUs).toFloat() / (endUs - startUs).toFloat())
                        .coerceIn(0f, 1f)
                } else {
                    1f
                }
                val brightness = if (fadeIn) progress else (1f - progress)
                return floatArrayOf(
                    brightness, 0f, 0f, 0f,
                    0f, brightness, 0f, 0f,
                    0f, 0f, brightness, 0f,
                    0f, 0f, 0f, 1f
                )
            }
        }
    }

    private fun createSlideInEffect(durationUs: Long): MatrixTransformation {
        return MatrixTransformation { presentationTimeUs ->
            val matrix = Matrix()
            if (presentationTimeUs < durationUs && durationUs > 0L) {
                val progress = (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                val translateX = (1f - progress) * 2f
                matrix.postTranslate(translateX, 0f)
            }
            matrix
        }
    }

    private fun extractLastFrameToTempFile(clip: VideoClip): Uri? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, clip.uri)
            val targetTimeUs = ((clip.trimEndMs - 50L).coerceAtLeast(clip.trimStartMs)) * 1000L
            val bitmap = retriever.getFrameAtTime(
                targetTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null

            val tempFile = File(context.cacheDir, "freeze_frame_${System.currentTimeMillis()}.png")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            bitmap.recycle()
            Uri.fromFile(tempFile)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}
