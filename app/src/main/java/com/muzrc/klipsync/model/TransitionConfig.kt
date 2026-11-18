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

data class TransitionConfig(
    val joinMode: JoinMode = JoinMode.SEQUENTIAL,
    val transitionType: TransitionType = TransitionType.NONE,
    val durationMs: Long = 800L
)
