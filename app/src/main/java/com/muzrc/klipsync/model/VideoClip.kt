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
    val motionEffect: CameraMotionEffect = CameraMotionEffect.NONE,
    val motionIntensity: Float = 0.20f // Julat 0.05f (5%) hingga 0.50f (50%)
) {
    init {
        require(originalDurationMs >= 0L) {
            "originalDurationMs mesti >= 0 (dapat $originalDurationMs)"
        }
        require(trimStartMs >= 0L) {
            "trimStartMs mesti >= 0 (dapat $trimStartMs)"
        }
        require(trimEndMs >= trimStartMs) {
            "trimEndMs ($trimEndMs) mesti >= trimStartMs ($trimStartMs)"
        }
    }

    val clampedSpeed: Float
        get() = speed.coerceIn(0.25f, 4.0f)

    val clampedLoopCount: Int
        get() = loopCount.coerceAtLeast(1)

    val clampedMotionIntensity: Float
        get() = motionIntensity.coerceIn(0.05f, 0.50f)

    val normalizedRotationDegrees: Int
        get() = ((rotationDegrees % 360) + 360) % 360

    val rawTrimmedDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)

    val singlePlayDurationMs: Long
        get() {
            if (isImage) return rawTrimmedDurationMs
            return (rawTrimmedDurationMs / clampedSpeed).toLong().coerceAtLeast(0L)
        }

    val effectiveDurationMs: Long
        get() = singlePlayDurationMs * clampedLoopCount
}
