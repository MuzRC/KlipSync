package com.muzrc.klipsync.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
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
import com.muzrc.klipsync.model.CameraMotionEffect
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
import com.muzrc.klipsync.util.ExportDebugLogger
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
            List(clip.clampedLoopCount.coerceIn(1, 20)) { clip }
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

        if (targetMinDurationMs > 0L && accumulatedMs < targetMinDurationMs) {
            ExportDebugLogger.log(
                "VIDEO_SEQ",
                "Amaran: accumulatedMs=$accumulatedMs < targetMinDurationMs=$targetMinDurationMs selepas $safetyIterations iterasi"
            )
        }

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
            (transitionConfig.clampedDurationMs / 2L).coerceAtLeast(0L)
        } else {
            0L
        }

        var runningDurationMs = 0L

        finalClipChain.forEachIndexed { index, clip ->
            val isFirst = index == 0
            val isLast = index == finalClipChain.lastIndex
            val safeSpeed = if (clip.isImage) 1.0f else clip.clampedSpeed

            val clipRawDurationMs = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(100L)
            val safeOverlapHalfMs = overlapHalfMs.coerceAtMost(clipRawDurationMs / 2)

            val adjustedStart = if (!isFirst) {
                (clip.trimStartMs + (safeOverlapHalfMs * safeSpeed).toLong()).coerceAtMost(clip.trimEndMs)
            } else {
                clip.trimStartMs
            }

            var adjustedEnd = if (!isLast) {
                (clip.trimEndMs - (safeOverlapHalfMs * safeSpeed).toLong()).coerceAtLeast(adjustedStart)
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
            val clipDurationUs = finalPlayedDurationMs * 1000L

            val clipStartUs = 0L
            val clipEndUs = clipDurationUs

            runningDurationMs += finalPlayedDurationMs

            val clipVideoEffects = mutableListOf<Effect>()

            // 1. Orientasi
            if (clip.normalizedRotationDegrees != 0 || clip.isMirrored) {
                val scaleRotate = ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(clip.normalizedRotationDegrees.toFloat())
                    .setScale(if (clip.isMirrored) -1f else 1f, 1f)
                    .build()
                clipVideoEffects.add(scaleRotate)
            }

            // 2. Speed change
            if (!clip.isImage && safeSpeed !in 0.99f..1.01f) {
                clipVideoEffects.add(
                    androidx.media3.effect.SpeedChangeEffect(safeSpeed)
                )
            }

            // 3. Gerakan kamera
            if (clip.motionEffect != CameraMotionEffect.NONE) {
                clipVideoEffects.add(
                    createCameraMotionEffect(
                        effect = clip.motionEffect,
                        intensity = clip.clampedMotionIntensity,
                        clipStartUs = clipStartUs,
                        clipDurationUs = clipDurationUs
                    )
                )
            }

            // 4. Transition effects (sebelum globalPostEffects)
            if (finalClipChain.size > 1 && transitionConfig.transitionType != TransitionType.NONE) {
                val transDurationUs = (transitionConfig.clampedDurationMs.coerceAtMost(finalPlayedDurationMs / 2L)) * 1000L
                val fadeInUs = if (!isFirst && transDurationUs > 0L) transDurationUs else 0L
                val fadeOutUs = if (!isLast && transDurationUs > 0L) transDurationUs else 0L

                when (transitionConfig.transitionType) {
                    TransitionType.CROSSFADE,
                    TransitionType.FADE_TO_BLACK -> {
                        if (fadeInUs > 0L || fadeOutUs > 0L) {
                            clipVideoEffects.add(
                                createClipFadeEffect(
                                    clipStartUs = clipStartUs,
                                    clipDurationUs = clipDurationUs,
                                    fadeInDurationUs = fadeInUs,
                                    fadeOutDurationUs = fadeOutUs
                                )
                            )
                        }
                    }

                    TransitionType.ZOOM_DISSOLVE -> {
                        if (fadeInUs > 0L || fadeOutUs > 0L) {
                            clipVideoEffects.add(
                                createClipFadeEffect(
                                    clipStartUs = clipStartUs,
                                    clipDurationUs = clipDurationUs,
                                    fadeInDurationUs = fadeInUs,
                                    fadeOutDurationUs = fadeOutUs
                                )
                            )
                        }
                        if (fadeInUs > 0L) {
                            clipVideoEffects.add(
                                createTransitionZoomEffect(
                                    startUs = clipStartUs,
                                    endUs = clipStartUs + fadeInUs,
                                    zoomIn = false
                                )
                            )
                        }
                        if (fadeOutUs > 0L) {
                            clipVideoEffects.add(
                                createTransitionZoomEffect(
                                    startUs = (clipEndUs - fadeOutUs).coerceAtLeast(clipStartUs),
                                    endUs = clipEndUs,
                                    zoomIn = true
                                )
                            )
                        }
                    }

                    TransitionType.SLIDE_LEFT -> {
                        if (fadeInUs > 0L) {
                            clipVideoEffects.add(
                                createSlideInEffect(
                                    clipStartUs = clipStartUs,
                                    durationUs = fadeInUs
                                )
                            )
                        }
                    }

                    TransitionType.NONE -> Unit
                }
            }

            // 5. Global post effects (terakhir)
            clipVideoEffects.addAll(globalPostEffects)

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
                    .setDurationUs(finalPlayedDurationMs * 1000L) // ← PEMBETULAN
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
                            audioConfig.clampedOriginalVolume
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

        if (hasFreezeFrame) {
            val safeFreezeMs = freezeFrameExtensionMs.coerceIn(100L, 10_000L)
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
                val freezeDurationUs = safeFreezeMs * 1000L

                val imageItem = MediaItem.Builder()
                    .setUri(freezeUri)
                    .setMimeType(freezeMime)
                    .setImageDurationMs(safeFreezeMs)
                    .build()

                val freezeEffects = mutableListOf<Effect>()
                if (lastClip.normalizedRotationDegrees != 0 || lastClip.isMirrored) {
                    freezeEffects.add(
                        ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(lastClip.normalizedRotationDegrees.toFloat())
                            .setScale(if (lastClip.isMirrored) -1f else 1f, 1f)
                            .build()
                    )
                }
                freezeEffects.add(
                    createSlowZoomEffect(
                        clipStartUs = 0L,
                        totalDurationUs = freezeDurationUs
                    )
                )
                freezeEffects.addAll(globalPostEffects)

                val freezeEditedItem = EditedMediaItem.Builder(imageItem)
                    .setRemoveAudio(true)
                    .setFrameRate(exportConfig.frameRate.fps)
                    .setDurationUs(freezeDurationUs) // ← PEMBETULAN
                    .setEffects(Effects(emptyList(), freezeEffects))
                    .build()

                editedItems.add(freezeEditedItem)
            }
        }

        return EditedMediaItemSequence(editedItems)
    }

    private fun resolveImageMimeType(clip: VideoClip): String {
        val raw = try {
            clip.mimeType ?: context.contentResolver.getType(clip.uri) ?: ""
        } catch (_: Exception) {
            clip.mimeType ?: ""
        }
        return when {
            raw.contains("png", ignoreCase = true) -> MimeTypes.IMAGE_PNG
            raw.contains("webp", ignoreCase = true) -> MimeTypes.IMAGE_WEBP
            else -> MimeTypes.IMAGE_JPEG
        }
    }

    private fun createCameraMotionEffect(
        effect: CameraMotionEffect,
        intensity: Float,
        clipStartUs: Long,
        clipDurationUs: Long
    ): MatrixTransformation {
        val safeIntensity = intensity.coerceIn(0.05f, 0.50f)
        return MatrixTransformation { presentationTimeUs ->
            val matrix = Matrix()
            val localTimeUs = (presentationTimeUs - clipStartUs).coerceAtLeast(0L)
            val progress = if (clipDurationUs > 0L) {
                (localTimeUs.toFloat() / clipDurationUs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            when (effect) {
                CameraMotionEffect.NONE -> Unit
                CameraMotionEffect.ZOOM_IN -> {
                    val scale = 1.0f + (safeIntensity * progress)
                    matrix.postScale(scale, scale)
                }
                CameraMotionEffect.ZOOM_OUT -> {
                    val scale = (1.0f + safeIntensity) - (safeIntensity * progress)
                    matrix.postScale(scale, scale)
                }
                CameraMotionEffect.PAN_LEFT -> {
                    val baseZoom = 1.0f + safeIntensity
                    matrix.postScale(baseZoom, baseZoom)
                    val maxTranslate = safeIntensity * 0.80f
                    val translateX = maxTranslate - (2f * maxTranslate * progress)
                    matrix.postTranslate(translateX, 0f)
                }
                CameraMotionEffect.PAN_RIGHT -> {
                    val baseZoom = 1.0f + safeIntensity
                    matrix.postScale(baseZoom, baseZoom)
                    val maxTranslate = safeIntensity * 0.80f
                    val translateX = -maxTranslate + (2f * maxTranslate * progress)
                    matrix.postTranslate(translateX, 0f)
                }
            }
            matrix
        }
    }

    private fun createSlowZoomEffect(
        clipStartUs: Long,
        totalDurationUs: Long
    ): MatrixTransformation {
        return MatrixTransformation { presentationTimeUs ->
            val matrix = Matrix()
            if (totalDurationUs > 0L) {
                val localTimeUs = (presentationTimeUs - clipStartUs).coerceAtLeast(0L)
                val progress = (localTimeUs.toFloat() / totalDurationUs.toFloat())
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

    private fun createClipFadeEffect(
        clipStartUs: Long,
        clipDurationUs: Long,
        fadeInDurationUs: Long,
        fadeOutDurationUs: Long
    ): Effect {
        return object : RgbMatrix {
            override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
                val localTimeUs = (presentationTimeUs - clipStartUs).coerceAtLeast(0L)
                var brightness = 1.0f

                if (fadeInDurationUs > 0L && localTimeUs < fadeInDurationUs) {
                    brightness *= (localTimeUs.toFloat() / fadeInDurationUs.toFloat()).coerceIn(0f, 1f)
                }
                if (fadeOutDurationUs > 0L && clipDurationUs > 0L) {
                    val fadeOutStartLocalUs = (clipDurationUs - fadeOutDurationUs).coerceAtLeast(0L)
                    if (localTimeUs > fadeOutStartLocalUs) {
                        val remainingUs = (clipDurationUs - localTimeUs).coerceAtLeast(0L)
                        brightness *= (remainingUs.toFloat() / fadeOutDurationUs.toFloat()).coerceIn(0f, 1f)
                    }
                }

                return floatArrayOf(
                    brightness, 0f, 0f, 0f,
                    0f, brightness, 0f, 0f,
                    0f, 0f, brightness, 0f,
                    0f, 0f, 0f, 1f
                )
            }
        }
    }

    private fun createSlideInEffect(
        clipStartUs: Long,
        durationUs: Long
    ): MatrixTransformation {
        return MatrixTransformation { presentationTimeUs ->
            val matrix = Matrix()
            val localTimeUs = (presentationTimeUs - clipStartUs).coerceAtLeast(0L)
            if (localTimeUs in 0L until durationUs && durationUs > 0L) {
                val progress = (localTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                val translateX = (1f - progress) * 1f
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
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: return null

            val evenW = (bitmap.width / 2) * 2
            val evenH = (bitmap.height / 2) * 2
            val cleanBitmap = if (evenW > 0 && evenH > 0 && (evenW != bitmap.width || evenH != bitmap.height)) {
                Bitmap.createBitmap(bitmap, 0, 0, evenW, evenH).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }
            val tempFile = File(context.cacheDir, "freeze_frame_${System.nanoTime()}.png")
            FileOutputStream(tempFile).use { out ->
                cleanBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            cleanBitmap.recycle()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
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
