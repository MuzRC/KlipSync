package com.muzrc.klipsync.ui.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
    onSelectAudio: (Uri, Boolean) -> Unit,
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
            onSelectAudio(uri, false)
        }
    }

    val videoAudioExtractorPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSelectAudio(uri, true)
        }
    }

    val fadeOptionsMs = listOf(0L, 1000L, 2000L, 3000L, 5000L)
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
        selectedLabelColor = MaterialTheme.colorScheme.primary
    )
    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { audioPicker.launch("audio/*") },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = "Pilih Lagu",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            OutlinedButton(
                onClick = {
                    videoAudioExtractorPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = "Ekstrak Video",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        if (audioConfig.uri != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onRemoveAudio,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Buang Lagu",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "Mula Lagu: ${TimeFormatter.formatPreciseMs(audioConfig.startOffsetMs)} / ${TimeFormatter.formatDurationMs(audioConfig.totalDurationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val maxOffset = (audioConfig.totalDurationMs - 500L).coerceAtLeast(1L).toFloat()
                    Slider(
                        value = audioConfig.startOffsetMs.coerceIn(0L, maxOffset.toLong()).toFloat(),
                        onValueChange = { onStartOffsetChange(it.toLong()) },
                        valueRange = 0f..maxOffset,
                        colors = sliderColors,
                        modifier = Modifier.height(28.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val nudges = listOf(-500L to "-0.5s", -100L to "-0.1s", 100L to "+0.1s", 500L to "+0.5s")
                        nudges.forEach { (deltaMs, label) ->
                            OutlinedButton(
                                onClick = { onStartOffsetChange(audioConfig.startOffsetMs + deltaMs) },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = label, fontSize = 11.sp, maxLines = 1, softWrap = false)
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
                        valueRange = 0f..1f,
                        colors = sliderColors,
                        modifier = Modifier.height(28.dp)
                    )

                    Text(
                        text = "Kesan Fade In",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        fadeOptionsMs.forEach { ms ->
                            val isSelected = audioConfig.fadeInDurationMs == ms
                            FilterChip(
                                selected = isSelected,
                                onClick = { onFadeInChange(ms) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                label = { Text("${ms / 1000}s", fontSize = 11.5.sp) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }

                    Text(
                        text = "Kesan Fade Out",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        fadeOptionsMs.forEach { ms ->
                            val isSelected = audioConfig.fadeOutDurationMs == ms
                            FilterChip(
                                selected = isSelected,
                                onClick = { onFadeOutChange(ms) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                label = { Text("${ms / 1000}s", fontSize = 11.5.sp) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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

                    OutlinedButton(
                        onClick = onToggleMuteOriginal,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (!audioConfig.isOriginalAudioMuted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (audioConfig.isOriginalAudioMuted) {
                                Icons.Rounded.VolumeOff
                            } else {
                                Icons.Rounded.VolumeUp
                            },
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = if (audioConfig.isOriginalAudioMuted) "Senyap (Muted)" else "Aktif",
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
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
                    valueRange = 0f..1f,
                    colors = sliderColors,
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}
