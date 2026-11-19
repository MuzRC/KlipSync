package com.muzrc.klipsync.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.util.TimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedTabPanel(
    selectedClip: VideoClip?,
    hasMultipleClips: Boolean,
    onSpeedChange: (String, Float) -> Unit,
    onApplyToAll: (Float) -> Unit
) {
    if (selectedClip == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Tambah atau pilih satu klip video untuk melaraskan kelajuan (Slow-Mo / Fast-Forward).",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val speedPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Klip Aktif: ${selectedClip.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Kelajuan Semasa: ${String.format(Locale.US, "%.2fx", selectedClip.speed)} • Durasi Baharu: ${TimeFormatter.formatPreciseMs(selectedClip.singlePlayDurationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    speedPresets.forEach { preset ->
                        FilterChip(
                            selected = abs(selectedClip.speed - preset) < 0.03f,
                            onClick = { onSpeedChange(selectedClip.id, preset) },
                            label = {
                                Text(
                                    when (preset) {
                                        0.5f -> "0.5x (Slow-Mo)"
                                        1.0f -> "1.0x (Normal)"
                                        2.0f -> "2.0x (Laju)"
                                        else -> "${preset}x"
                                    }
                                )
                            }
                        )
                    }
                }

                Slider(
                    value = selectedClip.speed,
                    onValueChange = { onSpeedChange(selectedClip.id, it) },
                    valueRange = 0.25f..3.0f
                )

                if (hasMultipleClips) {
                    OutlinedButton(
                        onClick = { onApplyToAll(selectedClip.speed) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guna Kelajuan Ini Pada Semua Klip")
                    }
                }
            }
        }
    }
}
