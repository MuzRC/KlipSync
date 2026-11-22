package com.muzrc.klipsync.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.muzrc.klipsync.model.AudioTrackConfig
import com.muzrc.klipsync.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaMetadataReader(private val context: Context) {

    private companion object {
        const val FALLBACK_VIDEO_DURATION_MS = 4000L
        const val FALLBACK_IMAGE_DURATION_MS = 4000L
    }

    suspend fun readMediaClip(uri: Uri): VideoClip = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: ""
        val lowerPath = uri.toString().lowercase()
        val isImg = mime.startsWith("image/") ||
            lowerPath.endsWith(".jpg") ||
            lowerPath.endsWith(".jpeg") ||
            lowerPath.endsWith(".png") ||
            lowerPath.endsWith(".webp")

        if (isImg) {
            readImageClip(uri, mime.ifBlank { "image/jpeg" })
        } else {
            readVideoClip(uri)
        }
    }

    suspend fun readImageClip(
        uri: Uri,
        detectedMime: String = "image/jpeg"
    ): VideoClip = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Gambar_${System.currentTimeMillis()}.jpg"
        var width = 1080
        var height = 1920

        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                width = options.outWidth
                height = options.outHeight
            }
        } catch (_: Exception) {
        }

        VideoClip(
            uri = uri,
            displayName = displayName,
            originalDurationMs = FALLBACK_IMAGE_DURATION_MS,
            trimStartMs = 0L,
            trimEndMs = FALLBACK_IMAGE_DURATION_MS,
            width = width,
            height = height,
            isImage = true,
            mimeType = detectedMime
        )
    }

    suspend fun readVideoClip(uri: Uri): VideoClip = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Video_${System.currentTimeMillis()}.mp4"
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 1080
        var height = 1920
        var detectedMime = "video/mp4"

        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val rawWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 1080

            val rawHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 1920

            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0

            if (rotation == 90 || rotation == 270) {
                width = rawHeight
                height = rawWidth
            } else {
                width = rawWidth
                height = rawHeight
            }

            detectedMime = context.contentResolver.getType(uri) ?: detectedMime
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        // Fallback jika metadata tidak dapat dibaca.
        if (durationMs <= 0L) {
            durationMs = FALLBACK_VIDEO_DURATION_MS
        }
        if (width <= 0 || height <= 0) {
            width = 1080
            height = 1920
        }

        VideoClip(
            uri = uri,
            displayName = displayName,
            originalDurationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            width = width,
            height = height,
            isImage = false,
            mimeType = detectedMime
        )
    }

    suspend fun readAudioTrack(
        uri: Uri,
        isExtractedFromVideo: Boolean = false
    ): AudioTrackConfig = withContext(Dispatchers.IO) {
        val rawName = resolveDisplayName(uri) ?: "Audio_${System.currentTimeMillis()}.mp3"
        val displayName = if (isExtractedFromVideo) {
            "Audio dari ${rawName.substringBeforeLast('.')}"
        } else {
            rawName
        }

        val retriever = MediaMetadataRetriever()
        var durationMs = 0L

        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val peaks = AudioWaveformExtractor.extractWaveformPeaks(context, uri)

        AudioTrackConfig(
            uri = uri,
            displayName = displayName,
            isExtractedFromVideo = isExtractedFromVideo,
            totalDurationMs = durationMs.coerceAtLeast(0L),
            startOffsetMs = 0L,
            waveformPeaks = peaks
        )
    }

    fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
