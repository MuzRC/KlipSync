package com.muzrc.klipsync.model

enum class JoinMode(val label: String) {
    SEQUENTIAL("Sambung Terus"),
    OVERLAP("Pertindihan (Overlap)")
}

enum class TransitionType(val label: String) {
    NONE("Tiada (Hard Cut)"),
    CROSSFADE("Crossfade / Larut"),
    FADE_TO_BLACK("Fade ke Hitam"),
    SLIDE_LEFT("Slide Kiri")
}

enum class VideoEndBehavior(val label: String) {
    FREEZE_FRAME("Freeze Frame"),
    LOOP_TO_AUDIO("Auto-Loop Video")
}

data class TransitionConfig(
    val joinMode: JoinMode = JoinMode.SEQUENTIAL,
    val transitionType: TransitionType = TransitionType.NONE,
    val durationMs: Long = 800L,
    val endBehavior: VideoEndBehavior = VideoEndBehavior.FREEZE_FRAME
)
