package com.muzrc.klipsync.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.muzrc.klipsync.BuildConfig
import com.muzrc.klipsync.model.UpdateCheckState
import com.muzrc.klipsync.model.UpdateReleaseData
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

object UpdateChecker {

    private const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/MuzRC/KlipSync/main/version.json"

    data class DownloadProgressSnapshot(
        val status: Int,
        val bytesDownloaded: Long,
        val bytesTotal: Long,
        val progressPercent: Int,
        val reason: Int,
        val localUri: String?
    )

    suspend fun checkForUpdates(): UpdateCheckState = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(VERSION_JSON_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckState.Error(
                    message = "Pelayan kemas kini memulangkan kod ralat ($responseCode).",
                    errorCode = UpdateCheckState.ErrorCode.NETWORK
                )
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val latestVersion = json.optString("latestVersion", BuildConfig.VERSION_NAME)
            val remoteVersionCode = json.optInt("versionCode", BuildConfig.VERSION_CODE)
            val releaseNotes = json.optString("releaseNotes", "Tiada nota perubahan.")
            val downloadUrl = json.optString(
                "downloadUrl",
                "https://github.com/MuzRC/KlipSync/releases/latest"
            )

            if (remoteVersionCode > BuildConfig.VERSION_CODE) {
                UpdateCheckState.UpdateAvailable(
                    UpdateReleaseData(
                        latestVersion = latestVersion,
                        versionCode = remoteVersionCode,
                        releaseNotes = releaseNotes,
                        downloadUrl = downloadUrl
                    )
                )
            } else {
                UpdateCheckState.UpToDate(currentVersion = BuildConfig.VERSION_NAME)
            }
        } catch (e: SocketTimeoutException) {
            UpdateCheckState.Error(
                message = "Sambungan ke pelayan kemas kini tamat masa. Sila cuba lagi.",
                errorCode = UpdateCheckState.ErrorCode.TIMEOUT
            )
        } catch (e: JSONException) {
            UpdateCheckState.Error(
                message = "Format maklumat kemas kini tidak sah.",
                errorCode = UpdateCheckState.ErrorCode.PARSE
            )
        } catch (e: Exception) {
            UpdateCheckState.Error(
                message = "Gagal menyemak kemas kini. Pastikan sambungan internet aktif.",
                errorCode = UpdateCheckState.ErrorCode.NETWORK
            )
        } finally {
            connection?.disconnect()
        }
    }

    fun enqueueApkDownload(context: Context, release: UpdateReleaseData): Long {
        if (!release.hasValidUrl) return -1L
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: return -1L

            val safeVersion = release.latestVersion.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "KlipSync-v$safeVersion.apk"

            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null) {
                val existingFile = File(downloadDir, fileName)
                if (existingFile.exists()) {
                    existingFile.delete()
                }
            }

            val request = DownloadManager.Request(Uri.parse(release.downloadUrl)).apply {
                setTitle("KlipSync v${release.latestVersion}")
                setDescription("Memuat turun kemas kini aplikasi...")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
            }

            dm.enqueue(request)
        } catch (_: Throwable) {
            -1L
        }
    }

    fun queryDownloadProgress(context: Context, downloadId: Long): DownloadProgressSnapshot? {
        if (downloadId <= 0L) return null
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return null

        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return null

            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

            val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_FAILED
            val downloaded = if (downloadedIdx >= 0) cursor.getLong(downloadedIdx).coerceAtLeast(0L) else 0L
            val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L
            val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else 0
            val localUri = if (uriIdx >= 0) cursor.getString(uriIdx) else null

            val percent = if (total > 0L) {
                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                100
            } else {
                0
            }

            return DownloadProgressSnapshot(
                status = status,
                bytesDownloaded = downloaded,
                bytesTotal = total,
                progressPercent = percent,
                reason = reason,
                localUri = localUri
            )
        }
        return null
    }

    fun cancelDownload(context: Context, downloadId: Long) {
        if (downloadId <= 0L) return
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.remove(downloadId)
        } catch (_: Throwable) {
        }
    }

    fun installDownloadedApk(
        context: Context,
        downloadId: Long,
        fallbackLocalUri: String? = null
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return false
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val apkUri = dm?.getUriForDownloadedFile(downloadId)
                ?: fallbackLocalUri?.let { Uri.parse(it) }
                ?: return false

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
