package com.muzrc.klipsync.ui.tabs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pratetap Penapis Warna",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = onReset,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Set Semula",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ColorFilterPreset.entries.forEach { preset ->
                        val isSelected = colorConfig.preset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPresetChange(preset) },
                            colors = chipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            label = { Text(preset.label, fontSize = 11.5.sp) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Text(
                    text = "Kecerahan (Brightness): ${String.format(Locale.US, "%+.2f", colorConfig.clampedBrightness)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Slider(
                    value = colorConfig.clampedBrightness,
                    onValueChange = onBrightnessChange,
                    valueRange = -0.5f..0.5f,
                    colors = sliderColors,
                    modifier = Modifier.height(28.dp)
                )

                Text(
                    text = "Kontras (Contrast): ${String.format(Locale.US, "%.2fx", colorConfig.clampedContrast)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = colorConfig.clampedContrast,
                    onValueChange = onContrastChange,
                    valueRange = 0.5f..1.5f,
                    colors = sliderColors,
                    modifier = Modifier.height(28.dp)
                )

                Text(
                    text = "Kepekatan Warna (Saturation): ${String.format(Locale.US, "%.2fx", colorConfig.clampedSaturation)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = colorConfig.clampedSaturation,
                    onValueChange = onSaturationChange,
                    valueRange = 0f..2f,
                    colors = sliderColors,
                    modifier = Modifier.height(28.dp)
                )
            }
        }
    }
}
