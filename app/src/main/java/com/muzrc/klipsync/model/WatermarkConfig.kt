package com.muzrc.klipsync.model

import android.net.Uri

enum class WatermarkPosition(val label: String) {
    TOP_LEFT("Kiri Atas"),
    TOP_RIGHT("Kanan Atas"),
    BOTTOM_LEFT("Kiri Bawah"),
    BOTTOM_RIGHT("Kanan Bawah"),
    CENTER("Tengah")
}

data class WatermarkConfig(
    val uri: Uri? = null,
    val displayName: String = "",
    val scalePercent: Float = 0.22f, // 0.10f ke 0.50f daripada lebar video
    val opacity: Float = 0.85f,      // 0.15f ke 1.0f
    val position: WatermarkPosition = WatermarkPosition.TOP_RIGHT
) {
    val hasWatermark: Boolean
        get() = uri != null

    val clampedScalePercent: Float
        get() = scalePercent.coerceIn(0.10f, 0.50f)

    val clampedOpacity: Float
        get() = opacity.coerceIn(0.15f, 1.0f)

    val safeDisplayName: String
        get() = displayName.ifBlank { "Logo" }
}
