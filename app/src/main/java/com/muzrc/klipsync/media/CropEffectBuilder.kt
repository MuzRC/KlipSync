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

    fun buildCropAndScaleEffects(
        aspectRatio: AspectRatioPreset,
        scaleMode: CropScaleMode,
        exportConfig: ExportConfig
    ): List<Effect> {
        val shortEdge = ((exportConfig.resolution.shortEdgePx / 2) * 2).coerceAtLeast(360)

        val layoutStrategy = when (scaleMode) {
            CropScaleMode.FILL_CROP -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            CropScaleMode.FIT_BLUR -> Presentation.LAYOUT_SCALE_TO_FIT
        }

        val targetRatio = aspectRatio.aspectRatioFloat
        if (targetRatio != null && targetRatio > 0f) {
            val targetWidth: Int
            val targetHeight: Int
            if (targetRatio >= 1f) {
                targetHeight = shortEdge
                targetWidth = (((shortEdge * targetRatio).roundToInt() / 2) * 2).coerceAtLeast(360)
            } else {
                targetWidth = shortEdge
                targetHeight = (((shortEdge / targetRatio).roundToInt() / 2) * 2).coerceAtLeast(360)
            }
            return listOf(
                Presentation.createForWidthAndHeight(
                    targetWidth,
                    targetHeight,
                    layoutStrategy
                )
            )
        }

        return listOf(Presentation.createForHeight(shortEdge))
    }
}
