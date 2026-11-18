package com.muzrc.klipsync.model

enum class EditorTab(val title: String) {
    CLIPS("Klip"),
    AUDIO("Audio"),
    CROP("Crop"),
    SUBTITLES("Teks")
}

data class EditorUiState(
    val clips: List<VideoClip> = emptyList(),
    val transitionConfig: TransitionConfig = TransitionConfig(),
    val audioConfig: AudioTrackConfig = AudioTrackConfig(),
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.TIKTOK_9_16,
    val subtitles: List<SubtitleItem> = emptyList(),
    val activeTab: EditorTab = EditorTab.CLIPS,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val exportConfig: ExportConfig = ExportConfig(),
    val exportState: ExportState = ExportState.Idle,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle
) {
    val totalVideoDurationMs: Long
        get() {
            if (clips.isEmpty()) return 0L
            val rawTotal = clips.sumOf { it.effectiveDurationMs }
            if (transitionConfig.joinMode == JoinMode.OVERLAP && clips.size > 1) {
                val maxOverlapPerJoin = (transitionConfig.durationMs).coerceAtLeast(0L)
                val totalOverlap = (clips.size - 1) * maxOverlapPerJoin
                return (rawTotal - totalOverlap).coerceAtLeast(0L)
            }
            return rawTotal
        }

    val finalTimelineDurationMs: Long
        get() = maxOf(totalVideoDurationMs, audioConfig.effectiveAudioDurationMs)

    val freezeFrameExtensionMs: Long
        get() = (audioConfig.effectiveAudioDurationMs - totalVideoDurationMs).coerceAtLeast(0L)
}
