package com.muzrc.klipsync.model

import java.util.UUID

enum class SubtitleFontFamily(val label: String) {
    SANS_SERIF("Sans-Serif Moden"),
    SERIF("Serif Klasik"),
    BOLD_DISPLAY("Tebal (Display)"),
    MONOSPACE("Monospace")
}

enum class SubtitlePosition(val label: String) {
    BOTTOM("Bawah"),
    CENTER("Tengah"),
    TOP("Atas")
}

data class SubtitleItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val fontFamily: SubtitleFontFamily = SubtitleFontFamily.SANS_SERIF,
    val fontSizeSp: Float = 22f,
    val textColorHex: Long = 0xFFFFFFFF, // ARGB Long (contoh 0xFFFFFFFF = putih penuh)
    val hasDarkBackground: Boolean = true,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM
) {
    init {
        require(startMs >= 0L) {
            "startMs mesti >= 0 (dapat $startMs)"
        }
        require(endMs >= startMs) {
            "endMs ($endMs) mesti >= startMs ($startMs)"
        }
    }

    val durationMs: Long
        get() = endMs - startMs

    val clampedFontSizeSp: Float
        get() = fontSizeSp.coerceIn(8f, 72f)

    /** ARGB Int untuk digunakan dengan Paint/TextPaint. */
    val textColorArgb: Int
        get() = textColorHex.toInt()

    val hasValidText: Boolean
        get() = text.isNotBlank() && durationMs > 0L
}
