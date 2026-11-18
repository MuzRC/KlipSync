package com.muzrc.klipsync.util

import java.util.Locale

object TimeFormatter {

    fun formatDurationMs(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatPreciseMs(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val tenths = (safeMs % 1000L) / 100L
        return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    }
}
