package com.muzrc.klipsync.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.Effect
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import com.google.common.collect.ImmutableList
import com.muzrc.klipsync.model.SubtitleFontFamily
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.SubtitlePosition
import kotlin.math.min

@UnstableApi
object SubtitleOverlayBuilder {

    fun buildSubtitleEffect(subtitles: List<SubtitleItem>): Effect? {
        if (subtitles.isEmpty()) return null
        val overlay = TimedSubtitleBitmapOverlay(subtitles)
        return OverlayEffect(ImmutableList.of(overlay))
    }

    private class TimedSubtitleBitmapOverlay(
        private val subtitles: List<SubtitleItem>
    ) : BitmapOverlay() {

        private var videoWidth = 1080
        private var videoHeight = 1920
        private var cachedBitmap: Bitmap? = null
        private var lastSignature: String = ""

        override fun configure(videoSize: Size) {
            super.configure(videoSize)
            videoWidth = videoSize.width.coerceAtLeast(1)
            videoHeight = videoSize.height.coerceAtLeast(1)
            cachedBitmap = null
            lastSignature = ""
        }

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val currentTimeMs = presentationTimeUs / 1000L
            val activeItems = subtitles.filter {
                currentTimeMs in it.startMs..it.endMs && it.hasValidText
            }

            val signature = buildSignature(activeItems)
            val existing = cachedBitmap
            if (existing != null && !existing.isRecycled && signature == lastSignature) {
                return existing
            }

            lastSignature = signature
            val bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            if (activeItems.isNotEmpty()) {
                drawSubtitles(canvas, activeItems)
            }

            cachedBitmap = bitmap
            return bitmap
        }

        private fun buildSignature(items: List<SubtitleItem>): String {
            if (items.isEmpty()) return ""
            return items.joinToString("|") {
                "${it.id}:${it.text}:${it.textColorHex}:${it.fontSizeSp}:" +
                    "${it.fontFamily}:${it.position}:${it.hasDarkBackground}:" +
                    "${it.startMs}:${it.endMs}"
            }
        }

        private fun drawSubtitles(canvas: Canvas, items: List<SubtitleItem>) {
            val scaleFactor = min(videoWidth, videoHeight) / 360f
            val horizontalMargin = videoWidth * 0.08f
            val maxTextWidth = (videoWidth - horizontalMargin * 2).toInt().coerceAtLeast(100)

            items.forEach { item ->
                try {
                    drawSingleSubtitle(canvas, item, scaleFactor, horizontalMargin, maxTextWidth)
                } catch (_: Exception) {
                    // Skip item yang gagal supaya render tidak crash.
                }
            }
        }

        private fun drawSingleSubtitle(
            canvas: Canvas,
            item: SubtitleItem,
            scaleFactor: Float,
            horizontalMargin: Float,
            maxTextWidth: Int
        ) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.textColorArgb
                textSize = item.clampedFontSizeSp * scaleFactor
                typeface = resolveTypeface(item.fontFamily)
                if (!item.hasDarkBackground) {
                    setShadowLayer(4f * scaleFactor, 0f, 2f * scaleFactor, Color.BLACK)
                }
            }

            val staticLayout = StaticLayout.Builder
                .obtain(item.text
