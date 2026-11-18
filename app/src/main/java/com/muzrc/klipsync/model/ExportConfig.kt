package com.muzrc.klipsync.model

enum class ExportResolution(
    val label: String,
    val shortEdgePx: Int
) {
    HD_720P("720p HD (Pantas)", 720),
    FHD_1080P("1080p Full HD", 1080)
}

enum class ExportFrameRate(val label: String, val fps: Int) {
    FPS_30("30 FPS", 30),
    FPS_60("60 FPS", 60)
}

data class ExportConfig(
    val resolution: ExportResolution = ExportResolution.FHD_1080P,
    val frameRate: ExportFrameRate = ExportFrameRate.FPS_30
)

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(val progressPercent: Int) : ExportState
    data class Success(val savedUriString: String) : ExportState
    data class Error(val message: String) : ExportState
}
