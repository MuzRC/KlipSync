@UnstableApi
object ColorMatrixBuilder {

    fun buildColorEffect(config: ColorFilterConfig): Effect? {
        if (config.isDefault) return null
        val matrix = computeCombined4x4Matrix(config)
        return object : RgbMatrix {
            override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
        }
    }

    fun computeCombined4x4Matrix(config: ColorFilterConfig): FloatArray {
        // Guna nilai yang sudah di-clamp
        val baseSat = config.clampedSaturation
        val baseCon = config.clampedContrast
        val baseBright = config.clampedBrightness

        val sat = when (config.preset) {
            ColorFilterPreset.BLACK_AND_WHITE -> 0f
            ColorFilterPreset.VINTAGE -> (baseSat * 0.72f).coerceIn(0f, 2f)
            ColorFilterPreset.VIVID -> (baseSat * 1.35f).coerceIn(0f, 2f)
            else -> baseSat
        }

        val con = when (config.preset) {
            ColorFilterPreset.MOODY_DARK -> (baseCon * 1.18f).coerceIn(0.5f, 1.8f)
            ColorFilterPreset.VIVID -> (baseCon * 1.15f).coerceIn(0.5f, 1.8f)
            else -> baseCon
        }

        val bright = when (config.preset) {
            ColorFilterPreset.MOODY_DARK -> baseBright - 0.08f
            else -> baseBright
        }.coerceIn(-1f, 1f)

        // ... (selebihnya sama seperti versi sebelumnya)
        // ...
    }
}
