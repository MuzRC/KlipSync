package com.muzrc.klipsync.media

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import com.muzrc.klipsync.model.AspectRatioPreset
import com.muzrc.klipsync.model.CropScaleMode
import com.muzrc.klipsync.model.ExportConfig
import kotlin.math.roundToInt

@UnstableApi
object CropEffectBuilder {

    private const val MIN_SHORT_EDGE_PX = 360

    fun buildCropAndScaleEffects(
        aspectRatio: AspectRatioPreset,
        scaleMode: CropScaleMode,
        exportConfig: ExportConfig
    ): List<Effect> {
        // Saiz sisi pendek (genap) untuk output.
        val shortEdge = ((exportConfig.resolution.shortEdgePx / 2) * 2)
            .coerceAtLeast(MIN_SHORT_EDGE_PX)

        val targetRatio = aspectRatio.aspectRatioFloat

        // Fallback: tiada aspect ratio → kekalkan ratio asal, tinggi = shortEdge.
        if (targetRatio == null || targetRatio.isNaN() || targetRatio <= 0f) {
            return listOf(Presentation.createForHeight(shortEdge))
        }

        // Kira dimensi output berdasarkan ratio, tanpa clamp berasingan
        // supaya aspect ratio tidak rosak.
        val targetWidth: Int
        val targetHeight: Int
        if (targetRatio >= 1f) {
            // Landscape atau square
            targetHeight = shortEdge
            targetWidth = evenUp((shortEdge * targetRatio).roundToInt())
        } else {
            // Portrait
            targetWidth = shortEdge
            targetHeight = evenUp((shortEdge / targetRatio).roundToInt())
        }

        return when (scaleMode) {
            CropScaleMode.FILL_CROP -> listOf(
                Presentation.createForWidthAndHeight(
                    targetWidth,
                    targetHeight,
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )
            )

            CropScaleMode.FIT_BLUR -> {
                // ⚠️ Media3 Presentation sahaja TIDAK boleh hasilkan blur background.
                // FIT_BLUR memerlukan Composition dengan dua layer:
                //   1. Background: fill-crop + blur effect
                //   2. Foreground: fit (letterbox)
                //
                // Implementasi penuh perlu dibuat di peringkat Composition,
                // bukan di sini. Buat masa ini, kita fallback ke FIT supaya
                // output sekurang-kurangnya tidak crash.
                //
                // TODO: Implementasi FIT_BLUR dengan Composition + BlurEffect.
                listOf(
                    Presentation.createForWidthAndHeight(
                        targetWidth,
                        targetHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
            }
        }
    }

    /** Bundarkan ke nombor genap terdekat (minimum 2). */
    private fun evenUp(value: Int): Int {
        val even = (value / 2) * 2
        return if (even < 2) 2 else even
    }
}
