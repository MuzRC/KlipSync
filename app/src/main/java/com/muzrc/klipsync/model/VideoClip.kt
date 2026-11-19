package com.muzrc.klipsync.model

import android.net.Uri
import java.util.UUID

enum class CameraMotionEffect(val label: String) {
    NONE("Tiada"),
    ZOOM_IN("Zoom In Perlahan"),
    ZOOM_OUT("Zoom Out Perlahan"),
    PAN_LEFT("Pan Kiri"),
    PAN_RIGHT("Pan Kanan")
}

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
    val height: Int = 1920,
    val isImage: Boolean = false,
    val mimeType: String? = null,
    val motionEffect: CameraMotionEffect = CameraMotionEffect.NONE
) {
    val rawTrimmedDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)

    val singlePlayDurationMs: Long
        get() {
            if (isImage) return rawTrimmedDurationMs
            val safeSpeed = speed.coerceIn(0.25f, 4.0f)
            return (rawTrimmedDurationMs / safeSpeed).toLong().coerceAtLeast(0L)
        }

    val effectiveDurationMs: Long
        get() = singlePlayDurationMs * loopCount.coerceAtLeast(1)
}
