package com.muzrc.klipsync.util

import com.muzrc.klipsync.BuildConfig
import com.muzrc.klipsync.model.UpdateCheckState
import com.muzrc.klipsync.model.UpdateReleaseData
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object UpdateChecker {

    private const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/MuzRC/KlipSync/main/version.json"

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
                    "Pelayan kemas kini memulangkan kod ralat ($responseCode)."
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
        } catch (e: Exception) {
            UpdateCheckState.Error(
                "Gagal menyemak kemas kini. Pastikan sambungan internet aktif."
            )
        } finally {
            connection?.disconnect()
        }
    }
}
