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
            // Invalidasi cache; jangan recycle bitmap lama kerana ia mungkin
            // masih digunakan oleh GPU. Biarkan GC handle.
            cachedBitmap = null
            lastSignature = ""
        }

        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val currentTimeMs = presentationTimeUs / 1000L
            val activeItems = subtitles.filter {
                currentTimeMs in it.startMs..it.endMs && it.text.isNotBlank()
            }

            // Signature mesti merangkumi semua properti visual supaya
            // perubahan warna/saiz/font/posisi mencetuskan bitmap baru.
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

            // Jangan recycle bitmap lama di sini — ia mungkin masih dalam
            // penggunaan GPU. Biarkan GC handle.
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
            // Guna sisi pendek supaya teks konsisten untuk portrait & landscape.
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
                color = parseColor(item.textColorHex)
                textSize = item.fontSizeSp * scaleFactor
                typeface = resolveTypeface(item.fontFamily)
                if (!item.hasDarkBackground) {
                    setShadowLayer(4f * scaleFactor, 0f, 2f * scaleFactor, Color.BLACK)
                }
            }

            val staticLayout = StaticLayout.Builder
                .obtain(item.text, 0, item.text.length, textPaint, maxTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(4f * scaleFactor, 1.0f)
                .setIncludePad(true)
                .build()

            val textHeight = staticLayout.height.toFloat()
            var maxLineWidth = 0f
            for (i in 0 until staticLayout.lineCount) {
                maxLineWidth = maxOf(maxLineWidth, staticLayout.getLineWidth(i))
            }

            val padH = 14f * scaleFactor
            val padV = 8f * scaleFactor

            val topY = when (item.position) {
                SubtitlePosition.TOP -> videoHeight * 0.10f
                SubtitlePosition.CENTER -> (videoHeight - textHeight) / 2f
                SubtitlePosition.BOTTOM -> videoHeight * 0.82f - textHeight
            }.coerceAtLeast(0f)

            if (item.hasDarkBackground) {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(175, 0, 0, 0)
                    style = Paint.Style.FILL
                }
                val rect = RectF(
                    (videoWidth - maxLineWidth) / 2f - padH,
                    topY - padV,
                    (videoWidth + maxLineWidth) / 2f + padH,
                    topY + textHeight + padV
                )
                val radius = 10f * scaleFactor
                canvas.drawRoundRect(rect, radius, radius, bgPaint)
            }

            canvas.save()
            canvas.translate(horizontalMargin, topY)
            staticLayout.draw(canvas)
            canvas.restore()
        }

        /**
         * Parse warna dari pelbagai format:
         * - "#RRGGBB" atau "#AARRGGBB" (dengan #)
         * - "RRGGBB" atau "AARRGGBB" (tanpa #)
         * - Integer ARGB
         * Jika tiada alpha, tambah alpha penuh (0xFF).
         */
        private fun parseColor(colorHex: String): Int {
            val trimmed = colorHex.trim()
            if (trimmed.isEmpty()) return Color.WHITE

            // Cuba parse sebagai integer (jika sudah ARGB)
            trimmed.toIntOrNull()?.let { return it }

            val hex = trimmed.removePrefix("#")
            return try {
                when (hex.length) {
                    6 -> (0xFF000000.toInt()) or hex.toLong(16).toInt() // RRGGBB → tambah alpha
                    8 -> hex.toLong(16).toInt()                        // AARRGGBB
                    else -> Color.WHITE
                }
            } catch (_: NumberFormatException) {
                Color.WHITE
            }
        }

        private fun resolveTypeface(family: SubtitleFontFamily): Typeface {
            return when (family) {
                SubtitleFontFamily.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                SubtitleFontFamily.SERIF -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                SubtitleFontFamily.BOLD_DISPLAY -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                SubtitleFontFamily.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
        }
    }
}
