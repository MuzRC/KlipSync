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
    val textColorHex: Long = 0xFFFFFFFF,
    val hasDarkBackground: Boolean = true,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM
)
