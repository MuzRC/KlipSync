package com.muzrc.klipsync.model

data class UpdateReleaseData(
    val latestVersion: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String
) {
    val hasValidUrl: Boolean
        get() = downloadUrl.startsWith("https://") || downloadUrl.startsWith("http://")
}

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data class Checking(val progress: Float = 0f) : UpdateCheckState
    data class UpToDate(val currentVersion: String) : UpdateCheckState
    data class UpdateAvailable(val release: UpdateReleaseData) : UpdateCheckState
    data class Error(
        val message: String,
        val errorCode: ErrorCode = ErrorCode.UNKNOWN
    ) : UpdateCheckState

    enum class ErrorCode {
        NETWORK, PARSE, TIMEOUT, UNKNOWN
    }
}
