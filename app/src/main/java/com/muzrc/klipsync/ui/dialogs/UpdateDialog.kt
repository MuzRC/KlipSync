package com.muzrc.klipsync.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzrc.klipsync.BuildConfig
import com.muzrc.klipsync.model.UpdateCheckState
import com.muzrc.klipsync.model.UpdateReleaseData
import java.util.Locale

@Composable
fun UpdateDialog(
    state: UpdateCheckState,
    onStartDownload: (UpdateReleaseData) -> Unit = {},
    onCancelDownload: (Long) -> Unit = {},
    onInstallApk: (Long, String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    if (state is UpdateCheckState.Idle || state is UpdateCheckState.Checking) return

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (state) {
                    is UpdateCheckState.UpdateAvailable -> "Kemas Kini Baharu Tersedia"
                    is UpdateCheckState.Downloading -> "Memuat Turun Kemas Kini"
                    is UpdateCheckState.ReadyToInstall -> "Muat Turun Selesai"
                    is UpdateCheckState.UpToDate -> "Aplikasi Sudah Terkini"
                    is UpdateCheckState.Error -> "Ralat Semakan"
                    else -> ""
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is UpdateCheckState.UpdateAvailable -> {
                        Text(
                            text = "Versi Semasa: v${BuildConfig.VERSION_NAME} → Baharu: v${state.release.latestVersion}",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Nota Perubahan:\n${state.release.releaseNotes}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    is UpdateCheckState.Downloading -> {
                        Text(
                            text = "KlipSync v${state.release.latestVersion}",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (state.totalBytes > 0L) {
                            val fraction = (state.progressPercent / 100f).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${state.progressPercent}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${formatBytesMb(state.downloadedBytes)} / ${formatBytesMb(state.totalBytes)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Menyambung muat turun... (${formatBytesMb(state.downloadedBytes)})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Muat turun kekal berjalan di latar belakang walaupun dialog ini disembunyikan.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is UpdateCheckState.ReadyToInstall -> {
                        Text(
                            text = "KlipSync v${state.release.latestVersion} telah siap dimuat turun.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tekan 'Pasang Sekarang' untuk memasang versi baharu. Jika Android meminta kebenaran sumber tidak diketahui, benarkan dan tekan semula butang pasang.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is UpdateCheckState.UpToDate -> {
                        Text(
                            text = "Anda sedang menggunakan KlipSync versi terkini (v${state.currentVersion}).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    is UpdateCheckState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateCheckState.UpdateAvailable -> {
                    Button(
                        onClick = {
                            if (state.release.hasValidUrl) {
                                onStartDownload(state.release)
                            } else {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.release.downloadUrl))
                                context.startActivity(intent)
                                onDismiss()
                            }
                        }
                    ) {
                        Text("Muat Turun & Pasang")
                    }
                }

                is UpdateCheckState.Downloading -> {
                    TextButton(onClick = onDismiss) {
                        Text("Sembunyi")
                    }
                }

                is UpdateCheckState.ReadyToInstall -> {
                    Button(
                        onClick = {
                            onInstallApk(state.downloadId, state.localUriString)
                        }
                    ) {
                        Text("Pasang Sekarang")
                    }
                }

                else -> {
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            }
        },
        dismissButton = {
            when (state) {
                is UpdateCheckState.UpdateAvailable -> {
                    TextButton(onClick = onDismiss) {
                        Text("Nanti")
                    }
                }

                is UpdateCheckState.Downloading -> {
                    TextButton(onClick = { onCancelDownload(state.downloadId) }) {
                        Text(
                            text = "Batal",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is UpdateCheckState.ReadyToInstall -> {
                    TextButton(onClick = onDismiss) {
                        Text("Tutup")
                    }
                }

                else -> Unit
            }
        }
    )
}

private fun formatBytesMb(bytes: Long): String {
    if (bytes <= 0L) return "0.0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
