package com.muzrc.klipsync.model

enum class EditorTab(val title: String) {
    CLIPS("Klip"),
    TEMPLATES("Template"),
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
    val beatDetectionProgress: Float = 0f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val currentPositionMs: Long = 0L,
    val exportConfig: ExportConfig = ExportConfig(),
    val exportState: ExportState = ExportState.Idle,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    val statusMessage: String? = null,
    val activeTemplateId: String? = null
) {
    // ===== Pemilihan klip =====

    val selectedClip: VideoClip?
        get() = clips.find { it.id == selectedClipId } ?: clips.firstOrNull()

    // ===== Kandungan =====

    /** Adakah ada sekurang-kurangnya satu klip visual (video/gambar)? */
    val hasVisualContent: Boolean
        get() = clips.isNotEmpty()

    /** Adakah ada audio (muzik atau audio asal)? */
    val hasAudioContent: Boolean
        get() = audioConfig.hasMusic || !audioConfig.isOriginalAudioMuted

    /** Adakah ada overlay (subtitle/watermark)? */
    val hasOverlayContent: Boolean
        get() = subtitles.isNotEmpty() || watermarkConfig.uri != null

    /** Adakah projek boleh di-export? */
    val canExport: Boolean
        get() = hasVisualContent

    /** Untuk keserasian dengan kod lama; sama dengan canExport. */
    val hasAnyContent: Boolean
        get() = canExport

    // ===== Durasi =====

    /**
     * Bilangan klip selepas mengambil kira loopCount asas.
     * Nota: Tidak mengambil kira LOOP_TO_AUDIO (lihat expandedClipCount).
     */
    val baseExpandedClipCount: Int
        get() = clips.sumOf { it.loopCount.coerceAtLeast(1) }

    /**
     * Anggaran bilangan klip selepas mengambil kira LOOP_TO_AUDIO.
     * Ini adalah anggaran kasar; VideoSequenceBuilder mengira secara berperingkat.
     */
    val expandedClipCount: Int
        get() {
            val base = baseExpandedClipCount
            if (transitionConfig.endBehavior == VideoEndBehavior.LOOP_TO_AUDIO &&
                audioConfig.hasMusic
            ) {
                val singlePassDuration = clips.sumOf {
                    it.singlePlayDurationMs * it.loopCount.coerceAtLeast(1)
                }
                if (singlePassDuration > 0L) {
                    val repeats = (audioConfig.effectiveAudioDurationMs / singlePassDuration + 1L)
                        .coerceAtMost(100L)
                    return (base * repeats).toInt()
                }
            }
            return base
        }

    /**
     * Jumlah durasi visual (sebelum mengambil kira audio).
     * Menggunakan singlePlayDurationMs yang sepatutnya sudah mengambil kira speed.
     */
    val totalVideoDurationMs: Long
        get() {
            if (clips.isEmpty()) return 0L
            val rawTotal = clips.sumOf {
                it.singlePlayDurationMs * it.loopCount.coerceAtLeast(1)
            }
            if (transitionConfig.joinMode == JoinMode.OVERLAP && expandedClipCount > 1) {
                val totalOverlap = (expandedClipCount - 1) * transitionConfig.durationMs.coerceAtLeast(0L)
                return (rawTotal - totalOverlap).coerceAtLeast(0L)
            }
            return rawTotal
        }

    /**
     * Durasi timeline akhir selepas mengambil kira audio dan endBehavior.
     * Konsisten dengan apa yang VideoSequenceBuilder hasilkan:
     * - Jika ada audio dan audio lebih panjang -> ikut audio (dengan FREEZE_FRAME/LOOP_TO_AUDIO).
     * - Jika tiada audio atau audio lebih pendek -> ikut video.
     */
    val finalTimelineDurationMs: Long
        get() {
            val videoDur = totalVideoDurationMs
            if (!audioConfig.hasMusic) return videoDur
            val audioDur = audioConfig.effectiveAudioDurationMs
            return when (transitionConfig.endBehavior) {
                VideoEndBehavior.FREEZE_FRAME,
                VideoEndBehavior.LOOP_TO_AUDIO -> maxOf(videoDur, audioDur)
                VideoEndBehavior.STOP -> minOf(videoDur, audioDur)
            }
        }

    /** Berapa lama audio melebihi video (jika ada). */
    val extraAudioExtensionMs: Long
        get() = (audioConfig.effectiveAudioDurationMs - totalVideoDurationMs).coerceAtLeast(0L)

    /** Durasi freeze frame yang akan ditambah (jika endBehavior == FREEZE_FRAME). */
    val freezeFrameExtensionMs: Long
        get() = if (transitionConfig.endBehavior == VideoEndBehavior.FREEZE_FRAME) {
            extraAudioExtensionMs
        } else {
            0L
        }

    /** Durasi auto-loop yang akan ditambah (jika endBehavior == LOOP_TO_AUDIO). */
    val autoLoopExtensionMs: Long
        get() = if (transitionConfig.endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
            extraAudioExtensionMs
        } else {
            0L
        }

    /** Posisi semasa yang di-clamp ke timeline. */
    val clampedPositionMs: Long
        get() = currentPositionMs.coerceIn(0L, finalTimelineDurationMs)
}
