package com.muzrc.klipsync.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muzrc.klipsync.model.AspectRatioPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CropTabPanel(
    selectedRatio: AspectRatioPreset,
    onSelectRatio: (AspectRatioPreset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Pilih Nisbah Skrin & Crop",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "Bingkai video akan dipotong secara automatik ke bahagian tengah mengikut platform pilihan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AspectRatioPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedRatio == preset,
                    onClick = { onSelectRatio(preset) },
                    label = {
                        Text(
                            text = preset.label,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                )
            }
        }
    }
}
