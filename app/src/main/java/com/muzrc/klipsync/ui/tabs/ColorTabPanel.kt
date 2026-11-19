package com.muzrc.klipsync.ui.tabs

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muzrc.klipsync.model.ColorFilterConfig
import com.muzrc.klipsync.model.ColorFilterPreset
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorTabPanel(
    colorConfig: ColorFilterConfig,
    onPresetChange: (ColorFilterPreset) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onReset: () -> Unit
) {
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pratetap Penapis Warna",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = onReset) {
                        Text("Set Semula")
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorFilterPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = colorConfig.preset == preset,
                            onClick = { onPresetChange(preset) },
                            label = { Text(preset.label) }
                        )
                    }
                }

                Text(
                    text = "Kecerahan (Brightness): ${String.format(Locale.US, "%+.2f", colorConfig.brightness)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = colorConfig.brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = -0.4f..0.4f
                )

                Text(
                    text = "Kontras (Contrast): ${String.format(Locale.US, "%.2fx", colorConfig.contrast)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = colorConfig.contrast,
                    onValueChange = onContrastChange,
                    valueRange = 0.6f..1.5f
                )

                Text(
                    text = "Kepekatan Warna (Saturation): ${String.format(Locale.US, "%.2fx", colorConfig.saturation)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = colorConfig.saturation,
                    onValueChange = onSaturationChange,
                    valueRange = 0f..1.8f
                )
            }
        }
    }
}
