package com.muzrc.klipsync.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.muzrc.klipsync.model.AudioTrackConfig
import com.muzrc.klipsync.model.VideoClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaMetadataReader(private val context: Context) {

    suspend fun readVideoClip(uri: Uri): VideoClip = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Video_${System.currentTimeMillis()}.mp4"
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 1080
        var height = 1920

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
        } catch (_: Exception) {
            // Kekalkan nilai lalai jika metadata gagal dibaca
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        VideoClip(
            uri = uri,
            displayName = displayName,
            originalDurationMs = durationMs,
            trimStartMs = 0L,
            trimEndMs = durationMs,
            width = width,
            height = height
        )
    }

    suspend fun readAudioTrack(uri: Uri): AudioTrackConfig = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Audio_${System.currentTimeMillis()}.mp3"
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L

        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            // Kekalkan nilai lalai jika metadata gagal dibaca
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        AudioTrackConfig(
            uri = uri,
            displayName = displayName,
            totalDurationMs = durationMs,
            startOffsetMs = 0L
        )
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
