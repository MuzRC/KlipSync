package com.muzrc.klipsync.media

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import com.muzrc.klipsync.model.AspectRatioPreset
import com.muzrc.klipsync.model.ExportConfig

@UnstableApi
object CropEffectBuilder {

    fun buildCropAndScaleEffects(
        aspectRatio: AspectRatioPreset,
        exportConfig: ExportConfig
    ): List<Effect> {
        val effects = mutableListOf<Effect>()

        val targetRatio = aspectRatio.aspectRatioFloat
        if (targetRatio != null && targetRatio > 0f) {
            effects.add(
                Presentation.createForAspectRatio(
                    targetRatio,
                    Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                )
            )
        }

        effects.add(
            Presentation.createForShortSide(exportConfig.resolution.shortEdgePx)
        )

        return effects
    }
}
