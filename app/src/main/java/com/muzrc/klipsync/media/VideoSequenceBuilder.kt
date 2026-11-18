package com.muzrc.klipsync.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbFilter
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
        freezeFrameExtensionMs: Long
    ): EditedMediaItemSequence? {
        if (clips.isEmpty()) return null

        val editedItems = mutableListOf<EditedMediaItem>()
        val baseVideoEffects = mutableListOf<Effect>()
        baseVideoEffects.addAll(
            CropEffectBuilder.buildCropAndScaleEffects(aspectRatio, exportConfig)
        )
        SubtitleOverlayBuilder.buildSubtitleEffect(subtitles)?.let {
            baseVideoEffects.add(it)
        }

        val removeOriginalAudio = audioConfig.isOriginalAudioMuted ||
            audioConfig.originalVideoVolume <= 0.01f

        val overlapHalfMs = if (transitionConfig.joinMode == JoinMode.OVERLAP && clips.size > 1) {
            (transitionConfig.durationMs / 2L).coerceAtLeast(0L)
        } else {
            0L
        }

        clips.forEachIndexed { index, clip ->
            val isFirst = index == 0
            val isLast = index == clips.lastIndex

            val adjustedStart = if (!isFirst) {
                (clip.trimStartMs + overlapHalfMs).coerceAtMost(clip.trimEndMs)
            } else {
                clip.trimStartMs
            }

            val adjustedEnd = if (!isLast) {
                (clip.trimEndMs - overlapHalfMs).coerceAtLeast(adjustedStart)
            } else {
                clip.trimEndMs
            }

            val clipDurationMs = (adjustedEnd - adjustedStart).coerceAtLeast(100L)

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

            // Tambah kesan transisi di antara klip
            if (clips.size > 1 && transitionConfig.transitionType != TransitionType.NONE) {
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

        // Freeze frame jika audio lebih panjang daripada jumlah video
        if (freezeFrameExtensionMs > 0L) {
            val lastClip = clips.last()
            val freezeUri = extractLastFrameToTempFile(lastClip)
            if (freezeUri != null) {
                val imageItem = MediaItem.Builder()
                    .setUri(freezeUri)
                    .setImageDurationMs(freezeFrameExtensionMs)
                    .build()

                val freezeEditedItem = EditedMediaItem.Builder(imageItem)
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
        return RgbFilter.createGrayscaleFilter().let {
            object : androidx.media3.effect.RgbMatrix {
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
