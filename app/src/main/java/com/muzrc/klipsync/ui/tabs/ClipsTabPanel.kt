package com.muzrc.klipsync.ui.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzrc.klipsync.model.JoinMode
import com.muzrc.klipsync.model.TransitionConfig
import com.muzrc.klipsync.model.TransitionType
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.util.TimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipsTabPanel(
    clips: List<VideoClip>,
    transitionConfig: TransitionConfig,
    onAddClips: (List<Uri>) -> Unit,
    onRemoveClip: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onTrimClip: (String, Long, Long) -> Unit,
    onJoinModeChange: (JoinMode) -> Unit,
    onTransitionTypeChange: (TransitionType) -> Unit,
    onTransitionDurationChange: (Long) -> Unit
) {
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddClips(uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = {
                videoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(
                text = "Tambah Klip Video",
                modifier = Modifier.padding(start = 6.dp),
                fontWeight = FontWeight.SemiBold
            )
        }

        // Mod Cantuman & Transisi
        if (clips.size > 1) {
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
                    Text(
                        text = "Cara Gabungan Klip",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JoinMode.entries.forEach { mode ->
                            FilterChip(
                                selected = transitionConfig.joinMode == mode,
                                onClick = { onJoinModeChange(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    Text(
                        text = "Transisi Antara Klip",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransitionType.entries.forEach { type ->
                            FilterChip(
                                selected = transitionConfig.transitionType == type,
                                onClick = { onTransitionTypeChange(type) },
                                label = { Text(type.label) }
                            )
                        }
                    }

                    if (transitionConfig.joinMode == JoinMode.OVERLAP ||
                        transitionConfig.transitionType != TransitionType.NONE
                    ) {
                        val sec = transitionConfig.durationMs / 1000f
                        Text(
                            text = "Tempoh Pertindihan / Transisi: ${String.format("%.1f", sec)}s",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = transitionConfig.durationMs.toFloat(),
                            onValueChange = { onTransitionDurationChange(it.toLong()) },
                            valueRange = 200f..2500f
                        )
                    }
                }
            }
        }

        // Senarai Klip Terpilih
        clips.forEachIndexed { index, clip ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${index + 1}. ${clip.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row {
                            IconButton(
                                onClick = { onMoveUp(index) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Naik")
                            }
                            IconButton(
                                onClick = { onMoveDown(index) },
                                enabled = index < clips.lastIndex
                            ) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = "Turun")
                            }
                            IconButton(onClick = { onRemoveClip(clip.id) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Padam",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Text(
                        text = "Potong Klip: ${TimeFormatter.formatPreciseMs(clip.trimStartMs)} – ${TimeFormatter.formatPreciseMs(clip.trimEndMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (clip.originalDurationMs > 300L) {
                        RangeSlider(
                            value = clip.trimStartMs.toFloat()..clip.trimEndMs.toFloat(),
                            onValueChange = { range ->
                                onTrimClip(
                                    clip.id,
                                    range.start.toLong(),
                                    range.endInclusive.toLong()
                                )
                            },
                            valueRange = 0f..clip.originalDurationMs.toFloat()
                        )
                    }
                }
            }
        }
    }
}
