package com.muzrc.klipsync.model

enum class ExportResolution(
    val label: String,
    val shortEdgePx: Int
) {
    HD_720P("720p HD (Pantas)", 720),
    FHD_1080P("1080p Full HD", 1080)
}

enum class ExportFrameRate(val label: String, val fps: Int) {
    FPS_24("24 FPS (Sinematik)", 24),
    FPS_30("30 FPS", 30),
    FPS_60("60 FPS", 60)
}

data class ExportConfig(
    val resolution: ExportResolution = ExportResolution.FHD_1080P,
    val frameRate: ExportFrameRate = ExportFrameRate.FPS_30,
    /** Bitrate khusus dalam bps; null = auto kira berdasarkan resolusi & FPS. */
    val customBitrateBps: Int? = null
) {
    init {
        require(customBitrateBps == null || customBitrateBps > 0) {
            "customBitrateBps mesti null atau > 0 (dapat $customBitrateBps)"
        }
    }
}

enum class ExportStage {
    PREPARING, ENCODING, SAVING, FINALIZING
}

enum class ExportErrorCode {
    OOM,
    TIMEOUT,
    CODEC_UNSUPPORTED,
    PERMISSION_DENIED,
    STORAGE_FULL,
    UNKNOWN
}

sealed interface ExportState {
    data object Idle : ExportState

    data class Exporting(
        val progressPercent: Int,
        val stage: ExportStage = ExportStage.ENCODING
    ) : ExportState {
        init {
            require(progressPercent in 0..100) {
                "progressPercent mesti 0..100 (dapat $progressPercent)"
            }
        }
    }

    data class Success(
        val savedUriString: String,
        val durationMs: Long = 0L,
        val fileSizeBytes: Long = 0L
    ) : ExportState

    data class Error(
        val message: String,
        val errorCode: ExportErrorCode = ExportErrorCode.UNKNOWN
    ) : ExportState
}
