package com.muzrc.klipsync.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.muzrc.klipsync.model.AspectRatioPreset
import com.muzrc.klipsync.model.AudioTrackConfig
import com.muzrc.klipsync.model.ColorFilterConfig
import com.muzrc.klipsync.model.CropScaleMode
import com.muzrc.klipsync.model.ExportConfig
import com.muzrc.klipsync.model.JoinMode
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.TransitionConfig
import com.muzrc.klipsync.model.TransitionType
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.model.VideoEndBehavior
import com.muzrc.klipsync.model.WatermarkConfig
import java.io.File
import java.io.FileOutputStream

@UnstableApi
class VideoSequenceBuilder(private val context: Context) {

    fun buildVideoSequence(
        clips: List<VideoClip>,
        transitionConfig: TransitionConfig,
        colorConfig: ColorFilterConfig,
        audioConfig: AudioTrackConfig,
        aspectRatio: AspectRatioPreset,
        cropScaleMode: CropScaleMode,
        subtitles: List<SubtitleItem>,
        watermarkConfig: WatermarkConfig,
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

        val globalPostEffects = mutableListOf<Effect>()
        globalPostEffects.addAll(
            CropEffectBuilder.buildCropAndScaleEffects(aspectRatio, cropScaleMode, exportConfig)
        )
        ColorMatrixBuilder.buildColorEffect(colorConfig)?.let {
            globalPostEffects.add(it)
        }
        SubtitleOverlayBuilder.buildSubtitleEffect(subtitles)?.let {
            globalPostEffects.add(it)
        }
        WatermarkOverlayBuilder.buildWatermarkEffect(context, watermarkConfig)?.let {
            globalPostEffects.add(it)
        }

        val hasFreezeFrame = transitionConfig.endBehavior == VideoEndBehavior.FREEZE_FRAME &&
            freezeFrameExtensionMs > 0L

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
            val safeSpeed = if (clip.isImage) 1.0f else clip.speed.coerceIn(0.25f, 4.0f)

            val adjustedStart = if (!isFirst) {
                (clip.trimStartMs + (overlapHalfMs * safeSpeed).toLong()).coerceAtMost(clip.trimEndMs)
            } else {
                clip.trimStartMs
            }

            var adjustedEnd = if (!isLast) {
                (clip.trimEndMs - (overlapHalfMs * safeSpeed).toLong()).coerceAtLeast(adjustedStart)
            } else {
                clip.trimEndMs
            }

            val playedDurMs = ((adjustedEnd - adjustedStart) / safeSpeed).toLong().coerceAtLeast(100L)
            if (targetMinDurationMs > 0L && runningDurationMs + playedDurMs > targetMinDurationMs) {
                val neededPlayedMs = (targetMinDurationMs - runningDurationMs).coerceAtLeast(100L)
                val neededRawMs = (neededPlayedMs * safeSpeed).toLong()
                adjustedEnd = (adjustedStart + neededRawMs).coerceAtMost(clip.trimEndMs)
            }

            val finalPlayedDurationMs = ((adjustedEnd - adjustedStart) / safeSpeed).toLong().coerceAtLeast(100L)
            runningDurationMs += finalPlayedDurationMs

            val clipVideoEffects = mutableListOf<Effect>()

            if (clip.rotationDegrees != 0 || clip.isMirrored) {
                val scaleRotate = ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(clip.rotationDegrees.toFloat())
                    .setScale(if (clip.isMirrored) -1f else 1f, 1f)
                    .build()
                clipVideoEffects.add(scaleRotate)
            }

            if (!clip.isImage && safeSpeed !in 0.99f..1.01f) {
                clipVideoEffects.add(
                    androidx.media3.effect.SpeedChangeEffect(safeSpeed)
                )
            }

            clipVideoEffects.addAll(globalPostEffects)

            if (finalClipChain.size > 1 && transitionConfig.transitionType != TransitionType.NONE) {
                val transDurationUs = (transitionConfig.durationMs.coerceAtMost(finalPlayedDurationMs / 2L)) * 1000L
                val clipDurationUs = finalPlayedDurationMs * 1000L

                when (transitionConfig.transitionType) {
                    TransitionType.CROSSFADE,
                    TransitionType.FADE_TO_BLACK -> {
                        if (!isFirst && transDurationUs > 0L) {
                            clipVideoEffects.add(
                                createFadeEffect(startUs = 0L, endUs = transDurationUs, fadeIn = true)
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

                    TransitionType.ZOOM_DISSOLVE -> {
                        if (!isFirst && transDurationUs > 0L) {
                            clipVideoEffects.add(
                                createFadeEffect(startUs = 0L, endUs = transDurationUs, fadeIn = true)
                            )
                            clipVideoEffects.add(
                                createTransitionZoomEffect(
                                    startUs = 0L,
                                    endUs = transDurationUs,
                                    zoomIn = false
                                )
                            )
                        }
                        if (!isLast && transDurationUs > 0L) {
                            val fadeOutStartUs = (clipDurationUs - transDurationUs).coerceAtLeast(0L)
                            clipVideoEffects.add(
                                createFadeEffect(
                                    startUs = fadeOutStartUs,
                                    endUs = clipDurationUs,
                                    fadeIn = false
                                )
                            )
                            clipVideoEffects.add(
                                createTransitionZoomEffect(
                                    startUs = fadeOutStartUs,
                                    endUs = clipDurationUs,
                                    zoomIn = true
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

            val editedClip = if (clip.isImage) {
                val resolvedMime = resolveImageMimeType(clip)
                val imageItem = MediaItem.Builder()
                    .setUri(clip.uri)
                    .setMimeType(resolvedMime)
                    .setImageDurationMs(finalPlayedDurationMs)
                    .build()

                EditedMediaItem.Builder(imageItem)
                    .setRemoveAudio(true)
                    .setFrameRate(exportConfig.frameRate.fps)
                    .setEffects(Effects(emptyList(), clipVideoEffects))
                    .build()
            } else {
                val clipping = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(adjustedStart)
                    .setEndPositionMs(adjustedEnd)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(clip.uri)
                    .setClippingConfiguration(clipping)
                    .build()

                val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
                if (!removeOriginalAudio) {
                    if (safeSpeed !in 0.99f..1.01f) {
                        val sonic = SonicAudioProcessor().apply {
                            setSpeed(safeSpeed)
                        }
                        audioProcessors.add(sonic)
                    }
                    audioProcessors.add(
                        AudioSequenceBuilder.createConstantVolumeProcessor(
                            audioConfig.originalVideoVolume
                        )
                    )
                }

                EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(removeOriginalAudio)
                    .setFrameRate(exportConfig.frameRate.fps)
                    .setEffects(Effects(audioProcessors, clipVideoEffects))
                    .build()
            }

            editedItems.add(editedClip)
        }

        // Sambungan hujung jika audio lebih panjang daripada jumlah gambar/video (Freeze Frame + Slow Zoom)
        if (hasFreezeFrame) {
            val lastClip = finalClipChain.last()
            val freezeUri = if (lastClip.isImage) {
                lastClip.uri
            } else {
                extractLastFrameToTempFile(lastClip)
            }
            val freezeMime = if (lastClip.isImage) {
                resolveImageMimeType(lastClip)
            } else {
                MimeTypes.IMAGE_PNG
            }

            if (freezeUri != null) {
                val imageItem = MediaItem.Builder()
                    .setUri(freezeUri)
                    .setMimeType(freezeMime)
                    .setImageDurationMs(freezeFrameExtensionMs)
                    .build()

                val freezeEffects = mutableListOf<Effect>()
                if (lastClip.rotationDegrees != 0 || lastClip.isMirrored) {
                    freezeEffects.add(
                        ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(lastClip.rotationDegrees.toFloat())
                            .setScale(if (lastClip.isMirrored) -1f else 1f, 1f)
                            .build()
                    )
                }
                freezeEffects.addAll(globalPostEffects)
                freezeEffects.add(createSlowZoomEffect(freezeFrameExtensionMs * 1000L))

                val freezeEditedItem = EditedMediaItem.Builder(imageItem)
                    .setRemoveAudio(true)
                    .setFrameRate(exportConfig.frameRate.fps)
                    .setEffects(Effects(emptyList(), freezeEffects))
                    .build()

                editedItems.add(freezeEditedItem)
            }
        }

        return EditedMediaItemSequence(editedItems)
    }

    private fun resolveImageMimeType(clip: VideoClip): String {
        val raw = clip.mimeType ?: context.contentResolver.getType(clip.uri) ?: ""
        return when {
            raw.contains("png", ignoreCase = true) -> MimeTypes.IMAGE_PNG
            raw.contains("webp", ignoreCase = true) -> MimeTypes.IMAGE_WEBP
            else -> MimeTypes.IMAGE_JPEG
        }
    }

    private fun createSlowZoomEffect(totalDurationUs: Long): MatrixTransformation {
        return MatrixTransformation { presentationTimeUs ->
            val matrix = Matrix()
            if (totalDurationUs > 0L) {
                val progress = (presentationTimeUs.toFloat() / totalDurationUs.toFloat())
                    .coerceIn(0f, 1f)
                val scale = 1.0f + (0.12f * progress)
                matrix.postScale(scale, scale)
            }
            matrix
        }
    }

    private fun createTransitionZoomEffect(
        startUs: Long,
        endUs: Long,
        zoomIn: Boolean
    ): MatrixTransformation {
        return MatrixTransformation { presentationTimeUs ->
            val matrix = Matrix()
            if (presentationTimeUs in startUs..endUs && endUs > startUs) {
                val progress = ((presentationTimeUs - startUs).toFloat() / (endUs - startUs).toFloat())
                    .coerceIn(0f, 1f)
                val scale = if (zoomIn) {
                    1.0f + (0.10f * progress)
                } else {
                    1.10f - (0.10f * progress)
                }
                matrix.postScale(scale, scale)
            }
            matrix
        }
    }

    private fun createFadeEffect(startUs: Long, endUs: Long, fadeIn: Boolean): Effect {
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
