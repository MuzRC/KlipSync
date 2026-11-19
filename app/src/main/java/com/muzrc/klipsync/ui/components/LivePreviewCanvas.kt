package com.muzrc.klipsync.ui.components

import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.muzrc.klipsync.media.ColorMatrixBuilder
import com.muzrc.klipsync.model.AspectRatioPreset
import com.muzrc.klipsync.model.ColorFilterConfig
import com.muzrc.klipsync.model.CropScaleMode
import com.muzrc.klipsync.model.SubtitleFontFamily
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.SubtitlePosition
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.model.WatermarkConfig
import com.muzrc.klipsync.model.WatermarkPosition
import com.muzrc.klipsync.preview.PreviewClipMotionVisual
import com.muzrc.klipsync.preview.PreviewTransitionVisual

@OptIn(UnstableApi::class)
@Composable
fun LivePreviewCanvas(
    videoPlayer: ExoPlayer,
    hasClips: Boolean,
    activeClip: VideoClip?,
    aspectRatio: AspectRatioPreset,
    cropScaleMode: CropScaleMode,
    colorConfig: ColorFilterConfig,
    subtitles: List<SubtitleItem>,
    watermarkConfig: WatermarkConfig,
    currentPositionMs: Long,
    totalVideoDurationMs: Long,
    freezeFrameExtensionMs: Long,
    isInFreezeFrameZone: Boolean,
    transitionVisual: PreviewTransitionVisual = PreviewTransitionVisual(),
    motionVisual: PreviewClipMotionVisual = PreviewClipMotionVisual(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val activeImageBitmap = remember(activeClip?.id, activeClip?.uri, activeClip?.isImage) {
        if (activeClip == null || !activeClip.isImage) return@remember null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(activeClip.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            var sample = 1
            while ((bounds.outWidth / sample) > 960 || (bounds.outHeight / sample) > 960) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(activeClip.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
            }
        } catch (_: Throwable) {
            null
        }
    }

    val watermarkBitmap = remember(watermarkConfig.uri) {
        val uri = watermarkConfig.uri ?: return@remember null
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!hasClips) {
            Text(
                text = "Tambah klip video atau gambar di bawah untuk mula menyunting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
            return@Box
        }

        val ratio = aspectRatio.aspectRatioFloat
        val frameModifier = if (ratio != null) {
            if (ratio >= 1f) {
                Modifier
                    .fillMaxWidth(0.94f)
                    .aspectRatio(ratio)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(ratio)
            }
        } else {
            Modifier.fillMaxSize()
        }

        val freezeZoomScale = if (isInFreezeFrameZone && freezeFrameExtensionMs > 0L) {
            val elapsed = (currentPositionMs - totalVideoDurationMs).coerceAtLeast(0L)
            val progress = (elapsed.toFloat() / freezeFrameExtensionMs.toFloat()).coerceIn(0f, 1f)
            1.0f + (0.12f * progress)
        } else {
            1.0f
        }

        val rotDeg = (activeClip?.rotationDegrees ?: 0).toFloat()
        val mirrorScaleX = if (activeClip?.isMirrored == true) -1f else 1f
        val combinedZoom = freezeZoomScale * transitionVisual.zoomScale * motionVisual.zoomScale
        val combinedOffsetXFraction = transitionVisual.slideOffsetXFraction + motionVisual.panOffsetXFraction

        val containerBg = if (cropScaleMode == CropScaleMode.FIT_BLUR) {
            Color(0xFF1A1E29)
        } else {
            Color.Black
        }

        val m = ColorMatrixBuilder.computeCombined4x4Matrix(colorConfig)
        val matrixValues = floatArrayOf(
            m[0], m[4], m[8], m[12], m[3] * 255f,
            m[1], m[5], m[9], m[13], m[7] * 255f,
            m[2], m[6], m[10], m[14], m[11] * 255f,
            0f, 0f, 0f, 1f, 0f
        )

        Box(
            modifier = frameModifier
                .clip(RoundedCornerShape(8.dp))
                .background(containerBg)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
        ) {
            if (activeClip?.isImage == true && activeImageBitmap != null) {
                val imgScale = if (
                    aspectRatio == AspectRatioPreset.ORIGINAL ||
                    cropScaleMode == CropScaleMode.FIT_BLUR
                ) {
                    ContentScale.Fit
                } else {
                    ContentScale.Crop
                }

                Image(
                    bitmap = activeImageBitmap,
                    contentDescription = activeClip.displayName,
                    contentScale = imgScale,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrixValues)),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = combinedZoom * mirrorScaleX
                            scaleY = combinedZoom
                            rotationZ = rotDeg
                            translationX = size.width * combinedOffsetXFraction
                        }
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            player = videoPlayer
                        }
                    },
                    update = { view ->
                        view.player = videoPlayer
                        view.resizeMode = if (
                            aspectRatio == AspectRatioPreset.ORIGINAL ||
                            cropScaleMode == CropScaleMode.FIT_BLUR
                        ) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }

                        val androidColorMatrix = android.graphics.ColorMatrix(matrixValues)
                        val paint = android.graphics.Paint().apply {
                            colorFilter = ColorMatrixColorFilter(androidColorMatrix)
                        }
                        view.videoSurfaceView?.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = combinedZoom * mirrorScaleX
                            scaleY = combinedZoom
                            rotationZ = rotDeg
                            translationX = size.width * combinedOffsetXFraction
                        }
                )
            }

            if (transitionVisual.blackOverlayAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = transitionVisual.blackOverlayAlpha.coerceIn(0f, 1f)))
                )
            }

            if (watermarkBitmap != null) {
                val wmAlign = when (watermarkConfig.position) {
                    WatermarkPosition.TOP_LEFT -> Alignment.TopStart
                    WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
                    WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
                    WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                    WatermarkPosition.CENTER -> Alignment.Center
                }
                Image(
                    bitmap = watermarkBitmap,
                    contentDescription = "Logo",
                    modifier = Modifier
                        .align(wmAlign)
                        .padding(10.dp)
                        .fillMaxWidth(watermarkConfig.scalePercent.coerceIn(0.08f, 0.55f))
                        .alpha(watermarkConfig.opacity.coerceIn(0.15f, 1.0f))
                )
            }

            if (isInFreezeFrameZone) {
                Text(
                    text = "SLOW ZOOM",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            val activeSubtitles = subtitles.filter {
                currentPositionMs in it.startMs..it.endMs && it.text.isNotBlank()
            }

            activeSubtitles.forEach { item ->
                val alignment = when (item.position) {
                    SubtitlePosition.TOP -> Alignment.TopCenter
                    SubtitlePosition.CENTER -> Alignment.Center
                    SubtitlePosition.BOTTOM -> Alignment.BottomCenter
                }

                val fontFamily = when (item.fontFamily) {
                    SubtitleFontFamily.SANS_SERIF -> FontFamily.SansSerif
                    SubtitleFontFamily.SERIF -> FontFamily.Serif
                    SubtitleFontFamily.BOLD_DISPLAY -> FontFamily.SansSerif
                    SubtitleFontFamily.MONOSPACE -> FontFamily.Monospace
                }

                val fontWeight = if (item.fontFamily == SubtitleFontFamily.BOLD_DISPLAY) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                }

                Box(
                    modifier = Modifier
                        .align(alignment)
                        .padding(horizontal = 14.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = item.text,
                        color = Color(item.textColorHex),
                        fontSize = (item.fontSizeSp * 0.72f).sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        textAlign = TextAlign.Center,
                        style = if (!item.hasDarkBackground) {
                            TextStyle(
                                shadow = Shadow(
                                    color = Color.Black,
                                    blurRadius = 6f
                                )
                            )
                        } else {
                            TextStyle.Default
                        },
                        modifier = if (item.hasDarkBackground) {
                            Modifier
                                .background(
                                    Color.Black.copy(alpha = 0.68f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}
