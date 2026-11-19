package com.muzrc.klipsync.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.media3.common.Effect
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import com.google.common.collect.ImmutableList
import com.muzrc.klipsync.model.WatermarkConfig
import com.muzrc.klipsync.model.WatermarkPosition
import kotlin.math.roundToInt

@UnstableApi
object WatermarkOverlayBuilder {

    fun buildWatermarkEffect(context: Context, config: WatermarkConfig): Effect? {
        val uri = config.uri ?: return null
        val sourceBitmap = try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            var sample = 1
            while ((boundsOpts.outWidth / sample) > 512 || (boundsOpts.outHeight / sample) > 512) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (_: Throwable) {
            null
        } ?: return null

        val overlay = LogoBitmapOverlay(sourceBitmap, config)
        return OverlayEffect(ImmutableList.of(overlay))
    }

    private class LogoBitmapOverlay(
        private val rawLogo: Bitmap,
        private val config: WatermarkConfig
    ) : BitmapOverlay() {

        private var composedBitmap: Bitmap? = null

        override fun configure(videoSize: Size) {
            super.configure(videoSize)
            val vidW = videoSize.width.coerceAtLeast(1)
            val vidH = videoSize.height.coerceAtLeast(1)

            val targetLogoW = (vidW * config.scalePercent.coerceIn(0.08f, 0.6f))
                .roundToInt()
                .coerceAtLeast(24)
            val aspect = rawLogo.height.toFloat() / rawLogo.width.coerceAtLeast(1).toFloat()
            val targetLogoH = (targetLogoW * aspect).roundToInt().coerceAtLeast(24)

            val scaledLogo = Bitmap.createScaledBitmap(rawLogo, targetLogoW, targetLogoH, true)
            val fullFrame = Bitmap.createBitmap(vidW, vidH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fullFrame)

            val margin = vidW * 0.04f
            val left = when (config.position) {
                WatermarkPosition.TOP_LEFT, WatermarkPosition.BOTTOM_LEFT -> margin
                WatermarkPosition.TOP_RIGHT, WatermarkPosition.BOTTOM_RIGHT -> vidW - targetLogoW - margin
                WatermarkPosition.CENTER -> (vidW - targetLogoW) / 2f
            }
            val top = when (config.position) {
                WatermarkPosition.TOP_LEFT, WatermarkPosition.TOP_RIGHT -> margin
                WatermarkPosition.BOTTOM_LEFT, WatermarkPosition.BOTTOM_RIGHT -> vidH - targetLogoH - margin
                WatermarkPosition.CENTER -> (vidH - targetLogoH) / 2f
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (config.opacity.coerceIn(0.1f, 1.0f) * 255).roundToInt()
            }

            canvas.drawBitmap(scaledLogo, left, top, paint)
            if (scaledLogo != rawLogo) {
                scaledLogo.recycle()
            }

            composedBitmap?.recycle()
            composedBitmap = fullFrame
        }

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            return composedBitmap ?: rawLogo
        }
    }
}
