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
    val loopCount: Int = 1,
    val speed: Float = 1.0f,
    val rotationDegrees: Int = 0,
    val isMirrored: Boolean = false,
    val width: Int = 1080,
    val height: Int = 1920
) {
    val rawTrimmedDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)

    val singlePlayDurationMs: Long
        get() {
            val safeSpeed = speed.coerceIn(0.25f, 4.0f)
            return (rawTrimmedDurationMs / safeSpeed).toLong().coerceAtLeast(0L)
        }

    val effectiveDurationMs: Long
        get() = singlePlayDurationMs * loopCount.coerceAtLeast(1)
}
