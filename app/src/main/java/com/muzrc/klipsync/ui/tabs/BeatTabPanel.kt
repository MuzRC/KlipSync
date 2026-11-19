package com.muzrc.klipsync.ui.tabs

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzrc.klipsync.media.BeatSensitivity
import com.muzrc.klipsync.util.TimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeatTabPanel(
    currentPositionMs: Long,
    beatMarkersMs: List<Long>,
    hasClips: Boolean,
    hasAudio: Boolean,
    isDetectingBeats: Boolean,
    onAutoDetectBeats: (BeatSensitivity) -> Unit,
    onAutoSyncClipsToBeats: (Boolean) -> Unit,
    onTapBeat: () -> Unit,
    onSeekToBeat: (Long) -> Unit,
    onCutAtBeat: (Long) -> Unit,
    onRemoveBeat: (Long) -> Unit,
    onClearBeats: () -> Unit
) {
    var selectedSensitivity by remember { mutableStateOf(BeatSensitivity.MEDIUM) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bahagian 1: Auto-Detect Beat dari Fail Lagu (100% Offline)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Auto-Kesan Beat Muzik (Offline)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Sensitiviti Ketukan:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BeatSensitivity.entries.forEach { sens ->
                        FilterChip(
                            selected = selectedSensitivity == sens,
                            onClick = { selectedSensitivity = sens },
                            label = { Text(sens.label, fontSize = 11.sp) }
                        )
                    }
                }

                Button(
                    onClick = { onAutoDetectBeats(selectedSensitivity) },
                    enabled = hasAudio && !isDetectingBeats,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDetectingBeats) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Sedang Menganalisis Rentak Lagu...",
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Text(
                            text = if (hasAudio) "Auto-Detect Beat Sekarang" else "Pilih Lagu di Tab Audio Dahulu",
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Bahagian 2: Auto-Sync Gambar / Klip mengikut Tanda Beat
        if (beatMarkersMs.isNotEmpty() && hasClips) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Selaraskan Visual ke Beat (${beatMarkersMs.size} tanda)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onAutoSyncClipsToBeats(false) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Padan Klip Sedia Ada",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = { onAutoSyncClipsToBeats(true) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Ulang & Isi Semua Beat",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Bahagian 3: Tap Beat Manual
        OutlinedButton(
            onClick = onTapBeat,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null)
            Text(
                text = "+ Tap Beat Manual (${TimeFormatter.formatPreciseMs(currentPositionMs)})",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Senarai Penanda Beat
        if (beatMarkersMs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Senarai Penanda Beat (${beatMarkersMs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onClearBeats) {
                    Text("Padam Semua")
                }
            }

            beatMarkersMs.forEachIndexed { idx, beatMs ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { onSeekToBeat(beatMs) }) {
                            Text(
                                text = "Beat #${idx + 1} • ${TimeFormatter.formatPreciseMs(beatMs)}",
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasClips) {
                                OutlinedButton(
                                    onClick = { onCutAtBeat(beatMs) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text("Belah", fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                            IconButton(onClick = { onRemoveBeat(beatMs) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Padam",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
