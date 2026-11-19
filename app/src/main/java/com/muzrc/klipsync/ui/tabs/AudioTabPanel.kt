package com.muzrc.klipsync.ui.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzrc.klipsync.model.AudioTrackConfig
import com.muzrc.klipsync.util.TimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioTabPanel(
    audioConfig: AudioTrackConfig,
    onSelectAudio: (Uri) -> Unit,
    onRemoveAudio: () -> Unit,
    onStartOffsetChange: (Long) -> Unit,
    onToggleMuteOriginal: () -> Unit,
    onOriginalVolumeChange: (Float) -> Unit,
    onMusicVolumeChange: (Float) -> Unit,
    onFadeInChange: (Long) -> Unit,
    onFadeOutChange: (Long) -> Unit
) {
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onSelectAudio(uri)
        }
    }

    val fadeOptionsMs = listOf(0L, 1000L, 2000L, 3000L, 5000L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { audioPicker.launch("audio/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.AudioFile, contentDescription = null)
            Text(
                text = if (audioConfig.uri == null) "Pilih Fail Lagu / Audio" else "Tukar Lagu",
                modifier = Modifier.padding(start = 6.dp),
                fontWeight = FontWeight.SemiBold
            )
        }

        if (audioConfig.uri != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = audioConfig.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onRemoveAudio) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Buang Lagu",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Slider Mula Lagu + Butang Fine-Tune Beat
                    Text(
                        text = "Mula Lagu: ${TimeFormatter.formatPreciseMs(audioConfig.startOffsetMs)} / ${TimeFormatter.formatDurationMs(audioConfig.totalDurationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val maxOffset = (audioConfig.totalDurationMs - 500L).coerceAtLeast(1L).toFloat()
                    Slider(
                        value = audioConfig.startOffsetMs.coerceIn(0L, maxOffset.toLong()).toFloat(),
                        onValueChange = { onStartOffsetChange(it.toLong()) },
                        valueRange = 0f..maxOffset
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val nudges = listOf(-500L to "-0.5s", -100L to "-0.1s", 100L to "+0.1s", 500L to "+0.5s")
                        nudges.forEach { (deltaMs, label) ->
                            OutlinedButton(
                                onClick = { onStartOffsetChange(audioConfig.startOffsetMs + deltaMs) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = label, fontSize = 11.sp)
                            }
                        }
                    }

                    Text(
                        text = "Kelantangan Lagu: ${(audioConfig.musicVolume * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = audioConfig.musicVolume,
                        onValueChange = onMusicVolumeChange,
                        valueRange = 0f..1f
                    )

                    Text(
                        text = "Kesan Fade In (Masuk Lembut)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fadeOptionsMs.forEach { ms ->
                            FilterChip(
                                selected = audioConfig.fadeInDurationMs == ms,
                                onClick = { onFadeInChange(ms) },
                                label = { Text("${ms / 1000}s") }
                            )
                        }
                    }

                    Text(
                        text = "Kesan Fade Out (Tamat Lembut)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fadeOptionsMs.forEach { ms ->
                            FilterChip(
                                selected = audioConfig.fadeOutDurationMs == ms,
                                onClick = { onFadeOutChange(ms) },
                                label = { Text("${ms / 1000}s") }
                            )
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Audio Asal Video",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedButton(onClick = onToggleMuteOriginal) {
                        Icon(
                            imageVector = if (audioConfig.isOriginalAudioMuted) {
                                Icons.Rounded.VolumeOff
                            } else {
                                Icons.Rounded.VolumeUp
                            },
                            contentDescription = null
                        )
                        Text(
                            text = if (audioConfig.isOriginalAudioMuted) "Senyap (Muted)" else "Aktif",
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                Text(
                    text = "Kelantangan Audio Video: ${(audioConfig.originalVideoVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = audioConfig.originalVideoVolume,
                    onValueChange = onOriginalVolumeChange,
                    valueRange = 0f..1f
                )
            }
        }
    }
}
