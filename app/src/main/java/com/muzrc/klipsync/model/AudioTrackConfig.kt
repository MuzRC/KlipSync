package com.muzrc.klipsync.model

import android.net.Uri

data class AudioTrackConfig(
    val uri: Uri? = null,
    val displayName: String = "",
    val isExtractedFromVideo: Boolean = false,
    val totalDurationMs: Long = 0L,
    val startOffsetMs: Long = 0L,
    val isOriginalAudioMuted: Boolean = true,
    val originalVideoVolume: Float = 0.5f,
    val musicVolume: Float = 1.0f,
    val fadeInDurationMs: Long = 0L,
    val fadeOutDurationMs: Long = 0L,
    val waveformPeaks: List<Float> = emptyList(),
    val beatMarkersMs: List<Long> = emptyList()
) {
    init {
        require(totalDurationMs >= 0L) {
            "totalDurationMs mesti >= 0 (dapat $totalDurationMs)"
        }
        require(startOffsetMs >= 0L) {
            "startOffsetMs mesti >= 0 (dapat $startOffsetMs)"
        }
        require(musicVolume in 0f..2f) {
            "musicVolume mesti dalam 0f..2f (dapat $musicVolume)"
        }
        require(originalVideoVolume in 0f..2f) {
            "originalVideoVolume mesti dalam 0f..2f (dapat $originalVideoVolume)"
        }
        require(fadeInDurationMs >= 0L) {
            "fadeInDurationMs mesti >= 0 (dapat $fadeInDurationMs)"
        }
        require(fadeOutDurationMs >= 0L) {
            "fadeOutDurationMs mesti >= 0 (dapat $fadeOutDurationMs)"
        }
    }

    /** Durasi audio berkesan selepas ditolak offset. */
    val effectiveAudioDurationMs: Long
        get() = (totalDurationMs - startOffsetMs).coerceAtLeast(0L)

    /** Adakah audio ini sah untuk dimainkan? */
    val hasMusic: Boolean
        get() = uri != null && effectiveAudioDurationMs > 0L

    /** Adakah offset dalam julat yang sah? */
    val hasValidOffset: Boolean
        get() = startOffsetMs in 0L..totalDurationMs

    /** Beat markers yang disusun (untuk UI yang memerlukan susunan). */
    val sortedBeatMarkersMs: List<Long>
        get() = beatMarkersMs.sorted()

    /**
     * Fade-in yang di-clamp supaya tidak melebihi separuh durasi audio.
     * Ini mengelak audio tidak pernah mencapai volume penuh.
     */
    val safeFadeInMs: Long
        get() = fadeInDurationMs.coerceIn(0L, effectiveAudioDurationMs / 2)

    /**
     * Fade-out yang di-clamp supaya tidak melebihi separuh durasi audio.
     */
    val safeFadeOutMs: Long
        get() = fadeOutDurationMs.coerceIn(0L, effectiveAudioDurationMs / 2)

    /** Volume muzik yang di-clamp ke julat yang disokong processor. */
    val clampedMusicVolume: Float
        get() = musicVolume.coerceIn(0f, 1.5f)

    /** Volume audio asal yang di-clamp. */
    val clampedOriginalVolume: Float
        get() = originalVideoVolume.coerceIn(0f, 1.5f)

    /** Salinan dengan offset baru, di-clamp ke julat sah. */
    fun withStartOffset(offsetMs: Long): AudioTrackConfig =
        copy(startOffsetMs = offsetMs.coerceIn(0L, totalDurationMs))

    /** Nama paparan dengan fallback. */
    val safeDisplayName: String
        get() = displayName.ifBlank { "Audio" }
}
