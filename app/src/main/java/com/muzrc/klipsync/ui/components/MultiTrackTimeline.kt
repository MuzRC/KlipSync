package com.muzrc.klipsync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
    selectedClipId: String?,
    audioConfig: AudioTrackConfig,
    subtitles: List<SubtitleItem>,
    freezeFrameExtensionMs: Long,
    autoLoopExtensionMs: Long,
    currentPositionMs: Long = 0L,
    onSelectClip: (String) -> Unit
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
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Trek 1: Klip Video / Gambar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (clips.isEmpty()) {
                    TimelinePill(
                        label = "Trek Visual: Kosong",
                        widthDp = 220,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        isSelected = false,
                        onClick = null
                    )
                } else {
                    clips.forEachIndexed { index, clip ->
                        val isSelected = clip.id == selectedClipId
                        val widthDp = ((clip.effectiveDurationMs / 1000f) * 18f)
                            .toInt()
                            .coerceIn(78, 240)
                        val prefix = if (clip.isImage) "G" else "V"
                        val speedTag = if (!clip.isImage && clip.speed !in 0.99f..1.01f) " ${clip.speed}x" else ""
                        val loopTag = if (clip.loopCount > 1) " [${clip.loopCount}x]" else ""
                        TimelinePill(
                            label = "$prefix${index + 1}$speedTag$loopTag (${TimeFormatter.formatPreciseMs(clip.effectiveDurationMs)})",
                            widthDp = widthDp,
                            containerColor = MaterialTheme.colorScheme.primary.copy(
                                alpha = if (isSelected) 0.36f else 0.20f
                            ),
                            textColor = MaterialTheme.colorScheme.primary,
                            isSelected = isSelected,
                            onClick = { onSelectClip(clip.id) }
                        )
                    }

                    if (freezeFrameExtensionMs > 0L) {
                        val freezeWidth = ((freezeFrameExtensionMs / 1000f) * 18f)
                            .toInt()
                            .coerceIn(104, 240)
                        TimelinePill(
                            label = "SlowZoom +${TimeFormatter.formatDurationMs(freezeFrameExtensionMs)}",
                            widthDp = freezeWidth,
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                            textColor = MaterialTheme.colorScheme.tertiary,
                            isSelected = false,
                            onClick = null
                        )
                    } else if (autoLoopExtensionMs > 0L) {
                        val loopWidth = ((autoLoopExtensionMs / 1000f) * 18f)
                            .toInt()
                            .coerceIn(112, 240)
                        TimelinePill(
                            label = "Auto-Loop +${TimeFormatter.formatDurationMs(autoLoopExtensionMs)}",
                            widthDp = loopWidth,
                            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f),
                            textColor = MaterialTheme.colorScheme.primary,
                            isSelected = false,
                            onClick = null
                        )
                    }
                }
            }

            // Trek 2: Gelombang Bunyi Audio (Waveform) + Titik Penanda Beat + Playhead
            val audioWidth = if (audioConfig.uri != null) {
                ((audioConfig.effectiveAudioDurationMs / 1000f) * 18f).toInt().coerceIn(220, 520)
            } else {
                220
            }

            Box(
                modifier = Modifier
                    .width(audioWidth.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (audioConfig.uri != null) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            ) {
                if (audioConfig.uri != null) {
                    val barColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                    val beatColor = MaterialTheme.colorScheme.tertiary
                    val playheadColor = Color(0xFFFF4D4D)
                    val effectiveDur = audioConfig.effectiveAudioDurationMs.coerceAtLeast(1L)

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val peaks = audioConfig.waveformPeaks
                        if (peaks.isNotEmpty()) {
                            val barStep = size.width / peaks.size.toFloat()
                            val barWidth = (barStep * 0.55f).coerceAtLeast(1.5f)

                            peaks.forEachIndexed { i, amp ->
                                val x = i * barStep + (barStep / 2f)
                                val barH = (size.height * amp).coerceAtLeast(3f)
                                val topY = (size.height - barH) / 2f
                                drawLine(
                                    color = barColor,
                                    start = Offset(x, topY),
                                    end = Offset(x, topY + barH),
                                    strokeWidth = barWidth
                                )
                            }
                        }

                        // Garis penanda Beat
                        audioConfig.beatMarkersMs.forEach { beatMs ->
                            if (beatMs in 0L..effectiveDur) {
                                val bx = (beatMs.toFloat() / effectiveDur.toFloat()) * size.width
                                drawLine(
                                    color = beatColor,
                                    start = Offset(bx, 0f),
                                    end = Offset(bx, size.height),
                                    strokeWidth = 2.5f
                                )
                            }
                        }

                        // Garis Playhead (Penunjuk Masa Semasa)
                        if (currentPositionMs in 0L..effectiveDur) {
                            val px = (currentPositionMs.toFloat() / effectiveDur.toFloat()) * size.width
                            drawLine(
                                color = playheadColor,
                                start = Offset(px, 0f),
                                end = Offset(px, size.height),
                                strokeWidth = 3f
                            )
                        }
                    }
                }

                val audioLabel = if (audioConfig.uri != null) {
                    "♪ ${audioConfig.displayName} (${TimeFormatter.formatDurationMs(audioConfig.effectiveAudioDurationMs)})"
                } else {
                    "♪ Tiada lagu dipilih"
                }
                Text(
                    text = audioLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (audioConfig.uri != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 8.dp)
                )
            }

            // Trek 3: Ringkasan Lirik & Beat
            val subLabel = buildString {
                append("Teks: ${subtitles.size} baris")
                if (audioConfig.beatMarkersMs.isNotEmpty()) {
                    append(" • Beat: ${audioConfig.beatMarkersMs.size} tanda")
                }
            }
            TimelinePill(
                label = subLabel,
                widthDp = 240,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                isSelected = false,
                onClick = null
            )
        }
    }
}

@Composable
private fun TimelinePill(
    label: String,
    widthDp: Int,
    containerColor: Color,
    textColor: Color,
    isSelected: Boolean,
    onClick: (() -> Unit)?
) {
    val borderMod = if (isSelected) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
    } else {
        Modifier
    }

    val clickMod = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .then(borderMod)
            .then(clickMod)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
