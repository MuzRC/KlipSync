package com.muzrc.klipsync.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import com.muzrc.klipsync.model.AspectRatioPreset
import com.muzrc.klipsync.model.SubtitleFontFamily
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.SubtitlePosition

@OptIn(UnstableApi::class)
@Composable
fun LivePreviewCanvas(
    videoPlayer: ExoPlayer,
    hasClips: Boolean,
    aspectRatio: AspectRatioPreset,
    subtitles: List<SubtitleItem>,
    currentPositionMs: Long,
    totalVideoDurationMs: Long,
    freezeFrameExtensionMs: Long,
    isInFreezeFrameZone: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!hasClips) {
            Text(
                text = "Tambah klip video di bawah untuk mula menyunting",
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

        // Kira skala Slow Zoom masa nyata semasa berada dalam zon Freeze Frame
        val freezeZoomScale = if (isInFreezeFrameZone && freezeFrameExtensionMs > 0L) {
            val elapsed = (currentPositionMs - totalVideoDurationMs).coerceAtLeast(0L)
            val progress = (elapsed.toFloat() / freezeFrameExtensionMs.toFloat()).coerceIn(0f, 1f)
            1.0f + (0.12f * progress)
        } else {
            1.0f
        }

        Box(
            modifier = frameModifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
        ) {
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
                    view.resizeMode = if (aspectRatio == AspectRatioPreset.ORIGINAL) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = freezeZoomScale
                        scaleY = freezeZoomScale
                    }
            )

            if (isInFreezeFrameZone) {
                Text(
                    text = "SLOW ZOOM",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
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
                        .padding(horizontal = 14.dp, vertical = 20.dp)
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
