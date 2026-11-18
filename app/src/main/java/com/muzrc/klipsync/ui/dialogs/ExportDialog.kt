package com.muzrc.klipsync.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muzrc.klipsync.model.ExportConfig
import com.muzrc.klipsync.model.ExportFrameRate
import com.muzrc.klipsync.model.ExportResolution
import com.muzrc.klipsync.model.ExportState
import com.muzrc.klipsync.util.TimeFormatter

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

    AlertDialog(
        onDismissRequest = {
            if (exportState !is ExportState.Exporting) {
                onDismiss()
            }
        },
        title = { Text("Eksport Video MP4") },
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
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExportResolution.entries.forEach { res ->
                                FilterChip(
                                    selected = exportConfig.resolution == res,
                                    onClick = { onResolutionChange(res) },
                                    label = { Text(res.label) }
                                )
                            }
                        }

                        Text(
                            text = "Kadar Bingkai (Frame Rate)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExportFrameRate.entries.forEach { fps ->
                                FilterChip(
                                    selected = exportConfig.frameRate == fps,
                                    onClick = { onFrameRateChange(fps) },
                                    label = { Text(fps.label) }
                                )
                            }
                        }
                    }

                    is ExportState.Exporting -> {
                        Text(
                            text = "Sedang memproses dan menggabungkan video... ${exportState.progressPercent}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { exportState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    is ExportState.Success -> {
                        Text(
                            text = "Berjaya! Video telah disimpan ke folder Movies/KlipSync di dalam Galeri anda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, Uri.parse(exportState.savedUriString))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Kongsi Video KlipSync")
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Kongsi Video")
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
                Button(onClick = onStartExport) {
                    Text("Mula Eksport")
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
