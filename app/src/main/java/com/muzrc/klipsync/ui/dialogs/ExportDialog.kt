package com.muzrc.klipsync.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzrc.klipsync.model.ExportConfig
import com.muzrc.klipsync.model.ExportFrameRate
import com.muzrc.klipsync.model.ExportResolution
import com.muzrc.klipsync.model.ExportStage
import com.muzrc.klipsync.model.ExportState
import com.muzrc.klipsync.util.TimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(
    exportConfig: ExportConfig,
    exportState: ExportState,
    finalDurationMs: Long,
    onResolutionChange: (ExportResolution) -> Unit,
    onFrameRateChange: (ExportFrameRate) -> Unit,
    onStartExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
        selectedLabelColor = MaterialTheme.colorScheme.primary
    )

    AlertDialog(
        onDismissRequest = {
            if (exportState !is ExportState.Exporting) {
                onDismiss()
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Eksport Video MP4",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (exportState) {
                    is ExportState.Idle -> {
                        Text(
                            text = "Jumlah Durasi Akhir: ${TimeFormatter.formatDurationMs(finalDurationMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Kualiti Resolusi",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ExportResolution.entries.forEach { res ->
                                val isSelected = exportConfig.resolution == res
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onResolutionChange(res) },
                                    colors = chipColors,
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                                        selectedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    label = { Text(res.label, fontSize = 11.5.sp) },
                                    modifier = Modifier.height(34.dp)
                                )
                            }
                        }

                        Text(
                            text = "Kadar Bingkai (Frame Rate)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ExportFrameRate.entries.forEach { fps ->
                                val isSelected = exportConfig.frameRate == fps
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onFrameRateChange(fps) },
                                    colors = chipColors,
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                                        selectedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    label = { Text(fps.label, fontSize = 11.5.sp) },
                                    modifier = Modifier.height(34.dp)
                                )
                            }
                        }
                    }

                    is ExportState.Exporting -> {
                        val stageLabel = when (exportState.stage) {
                            ExportStage.PREPARING -> "Menyediakan..."
                            ExportStage.ENCODING -> "Mengekod video..."
                            ExportStage.SAVING -> "Menyimpan ke Galeri..."
                            ExportStage.FINALIZING -> "Menyelesaikan..."
                        }
                        Text(
                            text = "$stageLabel ${exportState.progressPercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { exportState.progressPercent / 100f },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    is ExportState.Success -> {
                        val hasMetadata =
                            exportState.fileSizeBytes > 0L || exportState.durationMs > 0L
                        val successText = if (hasMetadata) {
                            val sizeMb = exportState.fileSizeBytes / (1024.0 * 1024.0)
                            val durSec = exportState.durationMs / 1000.0
                            String.format(
                                Locale.US,
                                "Berjaya! %.1f saat, %.1f MB disimpan ke folder Movies/KlipSync.",
                                durSec,
                                sizeMb
                            )
                        } else {
                            "Berjaya! Video telah disimpan ke folder Movies/KlipSync di dalam Galeri anda."
                        }
                        Text(
                            text = successText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(
                                        Intent.EXTRA_STREAM,
                                        Uri.parse(exportState.savedUriString)
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Kongsi Video KlipSync")
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Kongsi Video", fontWeight = FontWeight.Bold)
                        }
                    }

                    is ExportState.Error -> {
                        Text(
                            text = exportState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (exportState is ExportState.Idle) {
                Button(
                    onClick = onStartExport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Mula Eksport", fontWeight = FontWeight.Bold)
                }
            } else if (exportState is ExportState.Success || exportState is ExportState.Error) {
                TextButton(onClick = onDismiss) {
                    Text("Tutup")
                }
            }
        },
        dismissButton = {
            if (exportState is ExportState.Idle || exportState is ExportState.Exporting) {
                TextButton(onClick = onDismiss) {
                    Text("Batal")
                }
            }
        }
    )
}
