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
        val effects = mutableListOf<Effect>()
        val shortEdge = exportConfig.resolution.shortEdgePx

        val layoutStrategy = when (scaleMode) {
            CropScaleMode.FILL_CROP -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            CropScaleMode.FIT_BLUR -> Presentation.LAYOUT_SCALE_TO_FIT
        }

        val targetRatio = aspectRatio.aspectRatioFloat
        if (targetRatio != null && targetRatio > 0f) {
            effects.add(
                Presentation.createForAspectRatio(
                    targetRatio,
                    layoutStrategy
                )
            )
            val targetHeight = if (targetRatio >= 1f) {
                shortEdge
            } else {
                (shortEdge / targetRatio).roundToInt()
            }
            effects.add(Presentation.createForHeight(targetHeight))
        } else {
            effects.add(Presentation.createForHeight(shortEdge))
        }

        return effects
    }
}
