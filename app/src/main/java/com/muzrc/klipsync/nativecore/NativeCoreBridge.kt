package com.muzrc.klipsync.nativecore

import android.util.Log

object NativeCoreBridge {

    private const val TAG = "KlipSyncRustCore"

    val isNativeLoaded: Boolean = try {
        System.loadLibrary("klipsync_core")
        Log.i(TAG, "Teras natif Rust libklipsync_core.so (Fasa 1-3) berjaya dimuatkan.")
        true
    } catch (t: Throwable) {
        Log.w(TAG, "Gagal memuatkan libklipsync_core.so, guna mod sandaran Kotlin: ${t.message}")
        false
    }

    @JvmStatic
    external fun detectBeatsNative(
        pcmSamples: ShortArray,
        sampleRate: Int,
        channelCount: Int,
        minIntervalMs: Long,
        thresholdMultiplier: Float
    ): LongArray

    @JvmStatic
    external fun extractWaveformPeaksNative(
        pcmSamples: ShortArray,
        targetBars: Int
    ): FloatArray

    @JvmStatic
    external fun resizeArgbNative(
        srcPixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int
    ): IntArray

    @JvmStatic
    external fun composeFitBlurArgbNative(
        srcPixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        canvasWidth: Int,
        canvasHeight: Int
    ): IntArray

    @JvmStatic
    external fun parseSubtitlesNative(
        rawContent: String,
        fallbackEndDurationMs: Long
    ): Array<String>
}
