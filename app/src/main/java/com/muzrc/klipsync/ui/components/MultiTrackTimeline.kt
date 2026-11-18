package com.muzrc.klipsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzrc.klipsync.model.AudioTrackConfig
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.util.TimeFormatter

@Composable
fun MultiTrackTimeline(
    clips: List<VideoClip>,
    audioConfig: AudioTrackConfig,
    subtitles: List<SubtitleItem>,
    freezeFrameExtensionMs: Long
) {
    val scrollState = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Trek 1: Klip Video + Freeze Frame
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (clips.isEmpty()) {
                    TimelinePill(
                        label = "Trek Video: Kosong",
                        widthDp = 220,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    clips.forEachIndexed { index, clip ->
                        val widthDp = ((clip.effectiveDurationMs / 1000f) * 18f)
                            .toInt()
                            .coerceIn(68, 220)
                        TimelinePill(
                            label = "V${index + 1} (${TimeFormatter.formatDurationMs(clip.effectiveDurationMs)})",
                            widthDp = widthDp,
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            textColor = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (freezeFrameExtensionMs > 0L) {
                        val freezeWidth = ((freezeFrameExtensionMs / 1000f) * 18f)
                            .toInt()
                            .coerceIn(96, 240)
                        TimelinePill(
                            label = "Freeze +${TimeFormatter.formatDurationMs(freezeFrameExtensionMs)}",
                            widthDp = freezeWidth,
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                            textColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Trek 2: Audio / Lagu
            val audioLabel = if (audioConfig.uri != null) {
                "♪ ${audioConfig.displayName} (Mula ${TimeFormatter.formatDurationMs(audioConfig.startOffsetMs)} • Baki ${TimeFormatter.formatDurationMs(audioConfig.effectiveAudioDurationMs)})"
            } else {
                "♪ Tiada lagu dipilih"
            }
            val audioWidth = if (audioConfig.uri != null) {
                ((audioConfig.effectiveAudioDurationMs / 1000f) * 18f).toInt().coerceIn(180, 460)
            } else {
                220
            }
            TimelinePill(
                label = audioLabel,
                widthDp = audioWidth,
                containerColor = if (audioConfig.uri != null) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.26f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                textColor = if (audioConfig.uri != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            // Trek 3: Ringkasan Sarikata Manual
            val subLabel = if (subtitles.isEmpty()) {
                "Teks: Tiada sarikata manual"
            } else {
                "Teks: ${subtitles.size} baris lirik/sarikata aktif"
            }
            TimelinePill(
                label = subLabel,
                widthDp = 240,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimelinePill(
    label: String,
    widthDp: Int,
    containerColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
