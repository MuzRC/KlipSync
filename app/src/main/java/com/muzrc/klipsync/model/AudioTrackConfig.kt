package com.muzrc.klipsync.model

import android.net.Uri

data class AudioTrackConfig(
    val uri: Uri? = null,
    val displayName: String = "",
    val isExtractedFromVideo: Boolean = false,
    val totalDurationMs: Long = 0L,
    val startOffsetMs: Long = 0L,
    val isOriginalAudioMuted: Boolean = true,
    val originalVideoVolume: Float = 0.0f,
    val musicVolume: Float = 1.0f,
    val fadeInDurationMs: Long = 0L,
    val fadeOutDurationMs: Long = 0L,
    val waveformPeaks: List<Float> = emptyList(),
    val beatMarkersMs: List<Long> = emptyList()
) {
    val effectiveAudioDurationMs: Long
        get() = (totalDurationMs - startOffsetMs).coerceAtLeast(0L)
}
