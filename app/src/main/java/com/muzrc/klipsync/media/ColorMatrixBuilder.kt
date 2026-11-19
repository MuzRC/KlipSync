package com.muzrc.klipsync.media

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import com.muzrc.klipsync.model.ColorFilterConfig
import com.muzrc.klipsync.model.ColorFilterPreset

@UnstableApi
object ColorMatrixBuilder {

    fun buildColorEffect(config: ColorFilterConfig): Effect? {
        val isDefault = config.preset == ColorFilterPreset.NONE &&
            config.brightness in -0.01f..0.01f &&
            config.contrast in 0.99f..1.01f &&
            config.saturation in 0.99f..1.01f

        if (isDefault) return null

        val matrix = computeCombined4x4Matrix(config)
        return object : RgbMatrix {
            override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
        }
    }

    fun computeCombined4x4Matrix(config: ColorFilterConfig): FloatArray {
        val sat = when (config.preset) {
            ColorFilterPreset.BLACK_AND_WHITE -> 0f
            ColorFilterPreset.VINTAGE -> (config.saturation * 0.72f).coerceIn(0f, 2f)
            ColorFilterPreset.VIVID -> (config.saturation * 1.35f).coerceIn(0f, 2f)
            else -> config.saturation.coerceIn(0f, 2f)
        }

        val con = when (config.preset) {
            ColorFilterPreset.MOODY_DARK -> (config.contrast * 1.18f).coerceIn(0.5f, 1.8f)
            ColorFilterPreset.VIVID -> (config.contrast * 1.15f).coerceIn(0.5f, 1.8f)
            else -> config.contrast.coerceIn(0.5f, 1.8f)
        }

        val bright = when (config.preset) {
            ColorFilterPreset.MOODY_DARK -> config.brightness - 0.08f
            else -> config.brightness
        }

        val (rTint, gTint, bTint) = when (config.preset) {
            ColorFilterPreset.CINEMATIC_WARM -> Triple(1.08f, 1.02f, 0.90f)
            ColorFilterPreset.MOODY_DARK -> Triple(0.92f, 0.96f, 1.06f)
            ColorFilterPreset.VINTAGE -> Triple(1.10f, 1.03f, 0.84f)
            else -> Triple(1.0f, 1.0f, 1.0f)
        }

        val invSat = 1f - sat
        val rLum = 0.213f * invSat
        val gLum = 0.715f * invSat
        val bLum = 0.072f * invSat

        val offset = (0.5f * (1f - con)) + bright

        // Column-major 4x4 matrix untuk OpenGL / Media3 RgbMatrix
        return floatArrayOf(
            (rLum + sat) * con * rTint, rLum * con * gTint, rLum * con * bTint, 0f,
            gLum * con * rTint, (gLum + sat) * con * gTint, gLum * con * bTint, 0f,
            bLum * con * rTint, bLum * con * gTint, (bLum + sat) * con * bTint, 0f,
            offset, offset, offset, 1f
        )
    }
}
