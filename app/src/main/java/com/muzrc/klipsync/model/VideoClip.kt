package com.muzrc.klipsync.model

import android.net.Uri
import java.util.UUID

data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val displayName: String,
    val originalDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = originalDurationMs,
    val width: Int = 1080,
    val height: Int = 1920
) {
    val effectiveDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
}
