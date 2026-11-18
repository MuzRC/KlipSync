package com.muzrc.klipsync.model

data class UpdateReleaseData(
    val latestVersion: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String
)

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpToDate(val currentVersion: String) : UpdateCheckState
    data class UpdateAvailable(val release: UpdateReleaseData) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}
