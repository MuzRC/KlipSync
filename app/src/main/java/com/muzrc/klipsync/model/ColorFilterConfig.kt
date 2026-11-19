package com.muzrc.klipsync.model

enum class ColorFilterPreset(val label: String) {
    NONE("Asal"),
    CINEMATIC_WARM("Hangat Sinematik"),
    MOODY_DARK("Moody Gelap"),
    BLACK_AND_WHITE("Hitam Putih"),
    VINTAGE("Vintage Klasik"),
    VIVID("Cerah & Kontras")
}

data class ColorFilterConfig(
    val preset: ColorFilterPreset = ColorFilterPreset.NONE,
    val brightness: Float = 0.0f,   // -0.5f ke 0.5f
    val contrast: Float = 1.0f,     // 0.5f ke 1.5f
    val saturation: Float = 1.0f    // 0.0f ke 2.0f
)
