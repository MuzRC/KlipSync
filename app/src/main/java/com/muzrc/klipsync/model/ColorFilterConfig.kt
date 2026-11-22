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
) {
    /** Brightness yang di-clamp ke julat yang didokumenkan. */
    val clampedBrightness: Float
        get() = brightness.coerceIn(-0.5f, 0.5f)

    /** Contrast yang di-clamp ke julat yang didokumenkan. */
    val clampedContrast: Float
        get() = contrast.coerceIn(0.5f, 1.5f)

    /** Saturation yang di-clamp ke julat yang didokumenkan. */
    val clampedSaturation: Float
        get() = saturation.coerceIn(0f, 2f)

    /**
     * Adakah config ini bersamaan dengan "tiada effect"?
     * Digunakan untuk skip processor jika tiada perubahan visual.
     */
    val isDefault: Boolean
        get() = preset == ColorFilterPreset.NONE &&
            brightness in -0.01f..0.01f &&
            contrast in 0.99f..1.01f &&
            saturation in 0.99f..1.01f

    /**
     * Adakah slider saturation berkesan untuk preset ini?
     * Untuk BLACK_AND_WHITE, saturation sentiasa 0, jadi slider tidak berguna.
     */
    val saturationEnabled: Boolean
        get() = preset != ColorFilterPreset.BLACK_AND_WHITE

    /**
     * Salinan dengan preset baru, mengekalkan slider semasa.
     */
    fun withPreset(newPreset: ColorFilterPreset): ColorFilterConfig =
        copy(preset = newPreset)

    /**
     * Salinan dengan preset baru dan slider di-reset ke default.
     */
    fun withPresetReset(newPreset: ColorFilterPreset): ColorFilterConfig =
        copy(
            preset = newPreset,
            brightness = 0f,
            contrast = 1f,
            saturation = 1f
        )
}