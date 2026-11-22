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
        }.coerceIn(-1f, 1f)

        // Tint per channel, dinormalisasi supaya jumlahnya ~3.0
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
        // Setiap baris output (R', G', B', A') = matrix * (R, G, B, A)
        //
        // Kita gabungkan: output = Contrast(Saturation(input)) * Tint
        // Sat matrix:  S = [rLum+sat, gLum,     bLum,
        //                   rLum,     gLum+sat, bLum,
        //                   rLum,     gLum,     bLum+sat]
        // Contrast:    C = con * S, offset = (0.5*(1-con)) + bright
        // Tint:        T = diag(rTint, gTint, bTint)
        //
        // Hasil: out = con * T * S * in + offset
        //
        // Baris R': [ (rLum+sat)*rTint, gLum*rTint, bLum*rTint, 0 ]
        // Baris G': [ rLum*gTint, (gLum+sat)*gTint, bLum*gTint, 0 ]
        // Baris B': [ rLum*bTint, gLum*bTint, (bLum+sat)*bTint, 0 ]
        // Baris A': [ 0, 0, 0, 1 ]
        // Baris offset: [offset, offset, offset, 0]  (ditambah pada RGB)
        //
        // Nota: Media3 RgbMatrix menggunakan matriks 4x4 yang di-apply pada
        // vektor (R, G, B, A) dengan baris ke-4 sebagai translation.
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
            // kerana Media3 menggunakan konvensyen [R,G,B,A] * M + offset
            offset,
            offset,
            offset,
            1f
        )
    }
}
