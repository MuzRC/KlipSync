package com.muzrc.klipsync.nativecore

import android.os.Build
import android.util.Log
import androidx.annotation.Keep

@Keep
object NativeCoreBridge {

    private const val TAG = "KlipSyncRustCore"
    private const val LIB_NAME = "klipsync_core"

    /**
     * True jika libklipsync_core.so berjaya dimuatkan.
     * Menggunakan `by lazy` untuk memastikan load berlaku sekali sahaja
     * (thread-safe) dan pada akses pertama (bukan semasa class init).
     */
    val isNativeLoaded: Boolean by lazy {
        try {
            System.loadLibrary(LIB_NAME)
            Log.i(TAG, "Teras natif Rust lib$LIB_NAME.so berjaya dimuatkan.")
            true
        } catch (t: Throwable) {
            val abis = try {
                Build.SUPPORTED_ABIS.joinToString()
            } catch (_: Throwable) {
                "unknown"
            }
            Log.w(
                TAG,
                "Gagal memuatkan lib$LIB_NAME.so (ABI: $abis), guna mod sandaran Kotlin: ${t.message}"
            )
            false
        }
    }

    // ===== Native functions (private; akses melalui wrapper safe di bawah) =====

    @JvmStatic
    private external fun detectBeatsNative(
        pcmSamples: ShortArray,
        sampleRate: Int,
        channelCount: Int,
        minIntervalMs: Long,
        thresholdMultiplier: Float
    ): LongArray

    @JvmStatic
    private external fun extractWaveformPeaksNative(
        pcmSamples: ShortArray,
        targetBars: Int
    ): FloatArray

    @JvmStatic
    private external fun resizeArgbNative(
        srcPixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): IntArray

    @JvmStatic
    private external fun composeFitBlurArgbNative(
        srcPixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        canvasWidth: Int,
        canvasHeight: Int
    ): IntArray

    @JvmStatic
    private external fun parseSubtitlesNative(
        rawContent: String,
        fallbackEndDurationMs: Long
    ): Array<String>

    // ===== Safe wrappers (public) =====

    /**
     * Detect beats dalam PCM. Return null jika native tidak tersedia atau gagal.
     */
    fun detectBeatsSafe(
        pcmSamples: ShortArray,
        sampleRate: Int,
        channelCount: Int,
        minIntervalMs: Long,
        thresholdMultiplier: Float
    ): LongArray? {
        if (!isNativeLoaded) return null
        if (pcmSamples.isEmpty()) return LongArray(0)
        if (sampleRate <= 0 || channelCount <= 0) return LongArray(0)
        return try {
            detectBeatsNative(
                pcmSamples = pcmSamples,
                sampleRate = sampleRate,
                channelCount = channelCount,
                minIntervalMs = minIntervalMs.coerceAtLeast(0L),
                thresholdMultiplier = thresholdMultiplier.coerceAtLeast(0f)
            )
        } catch (t: Throwable) {
            Log.e(TAG, "detectBeatsNative gagal", t)
            null
        }
    }

    /**
     * Extract waveform peaks. Return null jika native tidak tersedia atau gagal.
     */
    fun extractWaveformPeaksSafe(
        pcmSamples: ShortArray,
        targetBars: Int
    ): FloatArray? {
        if (!isNativeLoaded) return null
        if (pcmSamples.isEmpty() || targetBars <= 0) return FloatArray(0)
        return try {
            extractWaveformPeaksNative(pcmSamples, targetBars.coerceAtLeast(16))
        } catch (t: Throwable) {
            Log.e(TAG, "extractWaveformPeaksNative gagal", t)
            null
        }
    }

    /**
     * Resize ARGB image. Return null jika native tidak tersedia, saiz terlalu besar,
     * atau gagal. Caller mesti semak saiz output sama dengan `dstWidth * dstHeight`.
     */
    fun resizeArgbSafe(
        srcPixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): IntArray? {
        if (!isNativeLoaded) return null
        if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) return null
        if (srcPixels.size < srcWidth * srcHeight) {
            Log.e(TAG, "srcPixels.size (${srcPixels.size}) < ${srcWidth * srcHeight}")
            return null
        }
        // Had saiz ~100MB untuk elak OOM (25M pixel * 4 byte).
        val srcSize = srcWidth.toLong() * srcHeight
        val dstSize = dstWidth.toLong() * dstHeight
        if (srcSize > 25_000_000L || dstSize > 25_000_000L) {
            Log.e(TAG, "Saiz imej terlalu besar: ${srcSize} -> ${dstSize}")
            return null
        }
        return try {
            resizeArgbNative(srcPixels, srcWidth, srcHeight, dstWidth, dstHeight)
        } catch (t: Throwable) {
            Log.e(TAG, "resizeArgbNative gagal", t)
            null
        }
    }

    /**
     * Compose fit-blur ARGB image. Return null jika native tidak tersedia,
     * saiz terlalu besar, atau gagal.
     */
    fun composeFitBlurArgbSafe(
        srcPixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        canvasWidth: Int,
        canvasHeight: Int
    ): IntArray? {
        if (!isNativeLoaded) return null
        if (srcWidth <= 0 || srcHeight <= 0 || canvasWidth <= 0 || canvasHeight <= 0) return null
        if (srcPixels.size < srcWidth * srcHeight) {
            Log.e(TAG, "srcPixels.size (${srcPixels.size}) < ${srcWidth * srcHeight}")
            return null
        }
        val srcSize = srcWidth.toLong() * srcHeight
        val canvasSize = canvasWidth.toLong() * canvasHeight
        if (srcSize > 25_000_000L || canvasSize > 25_000_000L) {
            Log.e(TAG, "Saiz imej terlalu besar: ${srcSize} -> ${canvasSize}")
            return null
        }
        return try {
            composeFitBlurArgbNative(srcPixels, srcWidth, srcHeight, canvasWidth, canvasHeight)
        } catch (t: Throwable) {
            Log.e(TAG, "composeFitBlurArgbNative gagal", t)
            null
        }
    }

    /**
     * Parse subtitle content. Format setiap string dalam array:
     * "startMs|endMs|text". Return empty list jika native tidak tersedia
     * atau gagal.
     */
    fun parseSubtitlesSafe(
        rawContent: String,
        fallbackEndDurationMs: Long
    ): List<ParsedSubtitleLine> {
        if (!isNativeLoaded) return emptyList()
        if (rawContent.isBlank()) return emptyList()
        return try {
            val raw = parseSubtitlesNative(rawContent, fallbackEndDurationMs)
            raw.mapNotNull { line ->
                val parts = line.split("|", limit = 3)
                if (parts.size != 3) return@mapNotNull null
                val startMs = parts[0].toLongOrNull() ?: return@mapNotNull null
                val endMs = parts[1].toLongOrNull() ?: return@mapNotNull null
                val text = parts[2]
                if (text.isBlank() || endMs <= startMs) return@mapNotNull null
                ParsedSubtitleLine(startMs = startMs, endMs = endMs, text = text)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "parseSubtitlesNative gagal", t)
            emptyList()
        }
    }

    data class ParsedSubtitleLine(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    /**
     * Untuk debugging: dapatkan versi native library (jika ada).
     * Return "unknown" jika tidak tersedia.
     */
    fun getNativeVersionSafe(): String {
        if (!isNativeLoaded) return "not_loaded"
        return try {
            getNativeVersionNative()
        } catch (t: Throwable) {
            "error: ${t.message}"
        }
    }

    @JvmStatic
    private external fun getNativeVersionNative(): String
}
