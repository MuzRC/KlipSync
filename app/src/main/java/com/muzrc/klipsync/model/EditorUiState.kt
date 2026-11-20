package com.muzrc.klipsync.model

enum class EditorTab(val title: String) {
    CLIPS("Klip"),
    SPEED("Laju"),
    COLOR("Warna"),
    AUDIO("Audio"),
    BEAT("Beat"),
    CROP("Crop"),
    SUBTITLES("Lirik"),
    WATERMARK("Logo"),
    DEV_LOG("Dev Log")
}

data class EditorUiState(
    val projectName: String = "Projek Baharu",
    val clips: List<VideoClip> = emptyList(),
    val selectedClipId: String? = null,
    val transitionConfig: TransitionConfig = TransitionConfig(),
    val colorConfig: ColorFilterConfig = ColorFilterConfig(),
    val audioConfig: AudioTrackConfig = AudioTrackConfig(),
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.TIKTOK_9_16,
    val cropScaleMode: CropScaleMode = CropScaleMode.FILL_CROP,
    val subtitles: List<SubtitleItem> = emptyList(),
    val watermarkConfig: WatermarkConfig = WatermarkConfig(),
    val activeTab: EditorTab = EditorTab.CLIPS,
    val isPlaying: Boolean = false,
    val isDetectingBeats: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val currentPositionMs: Long = 0L,
    val exportConfig: ExportConfig = ExportConfig(),
    val exportState: ExportState = ExportState.Idle,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    val statusMessage: String? = null
) {
    val selectedClip: VideoClip?
        get() = clips.find { it.id == selectedClipId } ?: clips.firstOrNull()

    val hasAnyContent: Boolean
        get() = clips.isNotEmpty() || audioConfig.uri != null || subtitles.isNotEmpty() || watermarkConfig.uri != null

    val expandedClipCount: Int
        get() = clips.sumOf { it.loopCount.coerceAtLeast(1) }

    val totalVideoDurationMs: Long
        get() {
            if (clips.isEmpty()) return 0L
            val rawTotal = clips.sumOf { it.effectiveDurationMs }
            if (transitionConfig.joinMode == JoinMode.OVERLAP && expandedClipCount > 1) {
                val maxOverlapPerJoin = transitionConfig.durationMs.coerceAtLeast(0L)
                val totalOverlap = (expandedClipCount - 1) * maxOverlapPerJoin
                return (rawTotal - totalOverlap).coerceAtLeast(0L)
            }
            return rawTotal
        }

    val finalTimelineDurationMs: Long
        get() = maxOf(totalVideoDurationMs, audioConfig.effectiveAudioDurationMs)

    val extraAudioExtensionMs: Long
        get() = (audioConfig.effectiveAudioDurationMs - totalVideoDurationMs).coerceAtLeast(0L)

    val freezeFrameExtensionMs: Long
        get() = if (transitionConfig.endBehavior == VideoEndBehavior.FREEZE_FRAME) {
            extraAudioExtensionMs
        } else {
            0L
        }

    val autoLoopExtensionMs: Long
        get() = if (transitionConfig.endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
            extraAudioExtensionMs
        } else {
            0L
        }
}
