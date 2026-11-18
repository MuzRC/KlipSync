package com.muzrc.klipsync.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muzrc.klipsync.BuildConfig
import com.muzrc.klipsync.model.UpdateCheckState

@Composable
fun UpdateDialog(
    state: UpdateCheckState,
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
                    is UpdateCheckState.UpToDate -> "Aplikasi Sudah Terkini"
                    is UpdateCheckState.Error -> "Ralat Semakan"
                    else -> ""
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (state is UpdateCheckState.UpdateAvailable) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.release.downloadUrl))
                        context.startActivity(intent)
                        onDismiss()
                    }
                ) {
                    Text("Muat Turun APK")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            if (state is UpdateCheckState.UpdateAvailable) {
                TextButton(onClick = onDismiss) {
                    Text("Nanti")
                }
            }
        }
    )
}
