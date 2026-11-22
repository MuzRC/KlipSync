package com.muzrc.klipsync.media

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import com.muzrc.klipsync.model.ColorFilterConfig
import com.muzrc.klipsync.model.ColorFilterPreset

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
        // Guna nilai yang sudah di-clamp dari model.
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

        // Tint per channel, dinormalisasi supaya jumlahnya ~3.0.
        // Ini memastikan tint tidak mengubah kecerahan keseluruhan.
        val (rawRTint, rawGTint, rawBTint) = when (config.preset) {
            ColorFilterPreset.CINEMATIC_WARM -> Triple(1.08f, 1.02f, 0.90f)
            ColorFilterPreset.MOODY_DARK -> Triple(0.92f, 0.96f, 1.06f)
            ColorFilterPreset.VINTAGE -> Triple(1.10f, 1.03f, 0.84f)
            else -> Triple(1.0f, 1.0f, 1.0f)
        }
        val tintSum = rawRTint + rawGTint + rawBTint
        val tintNorm = if (tintSum > 0f) 3f / tintSum else 1f
        val rTint = rawRTint * tintNorm
        val gTint = rawGTint * tintNorm
        val bTint = rawBTint * tintNorm

        // Saturation matrix (simetri)
        val invSat = 1f - sat
        val rLum = 0.213f * invSat
        val gLum = 0.715f * invSat
        val bLum = 0.072f * invSat

        // Offset untuk contrast + brightness (ruang 0..1)
        val offset = (0.5f * (1f - con)) + bright

        // Matriks 4x4 row-major untuk Media3 RgbMatrix.
        // Susunan: [R_row0, G_row0, B_row0, A_row0,
        //           R_row1, G_row1, B_row1, A_row1,
        //           R_row2, G_row2, B_row2, A_row2,
        //           R_row3, G_row3, B_row3, A_row3]
        //
        // Formula: out = con * T * S * in + offset
        // S = saturation matrix, T = tint diagonal, C = contrast, offset = brightness
        //
        // Baris R': [ (rLum+sat)*rTint, gLum*rTint, bLum*rTint, 0 ] * con
        // Baris G': [ rLum*gTint, (gLum+sat)*gTint, bLum*gTint, 0 ] * con
        // Baris B': [ rLum*bTint, gLum*bTint, (bLum+sat)*bTint, 0 ] * con
        // Baris A': [ 0, 0, 0, 1 ]
        // Baris offset: [ offset, offset, offset, 0 ]  (ditambah pada RGB)
        return floatArrayOf(
            // Row 0 (R output)
            (rLum + sat) * con * rTint,
            gLum * con * rTint,
            bLum * con * rTint,
            0f,
            // Row 1 (G output)
            rLum * con * gTint,
            (gLum + sat) * con * gTint,
            bLum * con * gTint,
            0f,
            // Row 2 (B output)
            rLum * con * bTint,
            gLum * con * bTint,
            (bLum + sat) * con * bTint,
            0f,
            // Row 3 (A output) — translation untuk RGB diletak di sini
            offset,
            offset,
            offset,
            1f
        )
    }
}