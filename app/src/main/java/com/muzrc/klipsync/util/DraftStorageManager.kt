package com.muzrc.klipsync.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.muzrc.klipsync.media.AudioWaveformExtractor
import com.muzrc.klipsync.model.AspectRatioPreset
import com.muzrc.klipsync.model.AudioTrackConfig
import com.muzrc.klipsync.model.CameraMotionEffect
import com.muzrc.klipsync.model.ColorFilterConfig
import com.muzrc.klipsync.model.ColorFilterPreset
import com.muzrc.klipsync.model.CropScaleMode
import com.muzrc.klipsync.model.EditorUiState
import com.muzrc.klipsync.model.JoinMode
import com.muzrc.klipsync.model.SubtitleFontFamily
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.SubtitlePosition
import com.muzrc.klipsync.model.TransitionConfig
import com.muzrc.klipsync.model.TransitionType
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.model.VideoEndBehavior
import com.muzrc.klipsync.model.WatermarkConfig
import com.muzrc.klipsync.model.WatermarkPosition
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SavedDraftSummary(
    val fileName: String,
    val projectName: String,
    val savedAtMs: Long,
    val clipCount: Int
)

object DraftStorageManager {

    private fun getDraftsDir(context: Context): File {
        val dir = File(context.filesDir, "klipsync_drafts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getDraftMediaDir(context: Context): File {
        val dir = File(context.filesDir, "klipsync_draft_media")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun takePersistablePermissionIfPossible(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Menyalin fail media (video, gambar, audio, logo) ke storan dalaman aplikasi
     * supaya draf kekal boleh dimainkan walaupun aplikasi ditutup dan dibuka semula.
     */
    private fun persistUriToLocalMedia(
        context: Context,
        uri: Uri,
        fallbackExt: String
    ): Uri {
        if (uri.scheme == "file") {
            val existing = uri.path?.let { File(it) }
            if (existing != null && existing.exists()) return uri
        }

        val mediaDir = getDraftMediaDir(context)
        val hashKey = sha256Short(uri.toString())
        val safeExt = fallbackExt.trimStart('.').ifBlank { "bin" }
        val targetFile = File(mediaDir, "media_${hashKey}.$safeExt")

        if (targetFile.exists() && targetFile.length() > 0L) {
            return Uri.fromFile(targetFile)
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0L) {
                Uri.fromFile(targetFile)
            } else {
                uri
            }
        } catch (_: Exception) {
            uri
        }
    }

    private fun sha256Short(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return bytes.take(10).joinToString("") { "%02x".format(it) }
    }

    private fun guessExtension(displayName: String, defaultExt: String): String {
        val ext = displayName.substringAfterLast('.', "")
        return if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) {
            ext.lowercase()
        } else {
            defaultExt
        }
    }

    suspend fun saveDraft(
        context: Context,
        state: EditorUiState,
        customName: String
    ): SavedDraftSummary = withContext(Dispatchers.IO) {
        val safeTitle = customName.trim().ifEmpty { "Draf_${System.currentTimeMillis()}" }
        val now = System.currentTimeMillis()
        val fileName = "draft_${now}.json"

        val root = JSONObject().apply {
            put("projectName", safeTitle)
            put("savedAtMs", now)
            put("aspectRatio", state.aspectRatio.name)
            put("cropScaleMode", state.cropScaleMode.name)

            put("transition", JSONObject().apply {
                put("joinMode", state.transitionConfig.joinMode.name)
                put("transitionType", state.transitionConfig.transitionType.name)
                put("durationMs", state.transitionConfig.durationMs)
                put("endBehavior", state.transitionConfig.endBehavior.name)
            })

            put("color", JSONObject().apply {
                put("preset", state.colorConfig.preset.name)
                put("brightness", state.colorConfig.brightness.toDouble())
                put("contrast", state.colorConfig.contrast.toDouble())
                put("saturation", state.colorConfig.saturation.toDouble())
            })

            val clipsArr = JSONArray()
            state.clips.forEach { clip ->
                takePersistablePermissionIfPossible(context, clip.uri)
                val defaultExt = if (clip.isImage) "jpg" else "mp4"
                val ext = guessExtension(clip.displayName, defaultExt)
                val localUri = persistUriToLocalMedia(context, clip.uri, ext)

                clipsArr.put(JSONObject().apply {
                    put("id", clip.id)
                    put("uri", localUri.toString())
                    put("displayName", clip.displayName)
                    put("originalDurationMs", clip.originalDurationMs)
                    put("trimStartMs", clip.trimStartMs)
                    put("trimEndMs", clip.trimEndMs)
                    put("loopCount", clip.loopCount)
                    put("speed", clip.speed.toDouble())
                    put("rotationDegrees", clip.rotationDegrees)
                    put("isMirrored", clip.isMirrored)
                    put("width", clip.width)
                    put("height", clip.height)
                    put("isImage", clip.isImage)
                    put("mimeType", clip.mimeType ?: "")
                    put("motionEffect", clip.motionEffect.name)
                    put("motionIntensity", clip.motionIntensity.toDouble())
                })
            }
            put("clips", clipsArr)

            val audio = state.audioConfig
            val persistedAudioUri = audio.uri?.let { srcUri ->
                takePersistablePermissionIfPossible(context, srcUri)
                val defaultExt = if (audio.isExtractedFromVideo) "mp4" else "mp3"
                val ext = guessExtension(audio.displayName, defaultExt)
                persistUriToLocalMedia(context, srcUri, ext)
            }
            put("audio", JSONObject().apply {
                put("uri", persistedAudioUri?.toString() ?: "")
                put("displayName", audio.displayName)
                put("isExtractedFromVideo", audio.isExtractedFromVideo)
                put("totalDurationMs", audio.totalDurationMs)
                put("startOffsetMs", audio.startOffsetMs)
                put("isOriginalAudioMuted", audio.isOriginalAudioMuted)
                put("originalVideoVolume", audio.originalVideoVolume.toDouble())
                put("musicVolume", audio.musicVolume.toDouble())
                put("fadeInDurationMs", audio.fadeInDurationMs)
                put("fadeOutDurationMs", audio.fadeOutDurationMs)
                val beatsArr = JSONArray()
                audio.beatMarkersMs.forEach { beatsArr.put(it) }
                put("beatMarkersMs", beatsArr)
            })

            val subsArr = JSONArray()
            state.subtitles.forEach { sub ->
                subsArr.put(JSONObject().apply {
                    put("id", sub.id)
                    put("text", sub.text)
                    put("startMs", sub.startMs)
                    put("endMs", sub.endMs)
                    put("fontFamily", sub.fontFamily.name)
                    put("fontSizeSp", sub.fontSizeSp.toDouble())
                    put("textColorHex", sub.textColorHex)
                    put("hasDarkBackground", sub.hasDarkBackground)
                    put("position", sub.position.name)
                })
            }
            put("subtitles", subsArr)

            val wm = state.watermarkConfig
            val persistedWmUri = wm.uri?.let { srcUri ->
                takePersistablePermissionIfPossible(context, srcUri)
                val ext = guessExtension(wm.displayName, "png")
                persistUriToLocalMedia(context, srcUri, ext)
            }
            put("watermark", JSONObject().apply {
                put("uri", persistedWmUri?.toString() ?: "")
                put("displayName", wm.displayName)
                put("scalePercent", wm.scalePercent.toDouble())
                put("opacity", wm.opacity.toDouble())
                put("position", wm.position.name)
            })
        }

        val outFile = File(getDraftsDir(context), fileName)
        outFile.writeText(root.toString(2))

        SavedDraftSummary(
            fileName = fileName,
            projectName = safeTitle,
            savedAtMs = now,
            clipCount = state.clips.size
        )
    }

    suspend fun listDrafts(context: Context): List<SavedDraftSummary> = withContext(Dispatchers.IO) {
        val dir = getDraftsDir(context)
        val files = dir.listFiles { f -> f.extension == "json" } ?: return@withContext emptyList()
        files.mapNotNull { file ->
            try {
                val json = JSONObject(file.readText())
                val clipsCount = json.optJSONArray("clips")?.length() ?: 0
                SavedDraftSummary(
                    fileName = file.name,
                    projectName = json.optString("projectName", file.nameWithoutExtension),
                    savedAtMs = json.optLong("savedAtMs", file.lastModified()),
                    clipCount = clipsCount
                )
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.savedAtMs }
    }

    suspend fun loadDraft(context: Context, fileName: String): EditorUiState? = withContext(Dispatchers.IO) {
        val file = File(getDraftsDir(context), fileName)
        if (!file.exists()) return@withContext null

        try {
            val json = JSONObject(file.readText())
            val projectName = json.optString("projectName", "Projek Draf")
            val aspectRatio = enumOrDefault(json.optString("aspectRatio"), AspectRatioPreset.TIKTOK_9_16)
            val cropScaleMode = enumOrDefault(json.optString("cropScaleMode"), CropScaleMode.FILL_CROP)

            val transJson = json.optJSONObject("transition") ?: JSONObject()
            val transitionConfig = TransitionConfig(
                joinMode = enumOrDefault(transJson.optString("joinMode"), JoinMode.SEQUENTIAL),
                transitionType = enumOrDefault(transJson.optString("transitionType"), TransitionType.NONE),
                durationMs = transJson.optLong("durationMs", 800L).coerceIn(0L, 3_000L),
                endBehavior = enumOrDefault(transJson.optString("endBehavior"), VideoEndBehavior.FREEZE_FRAME)
            )

            val colorJson = json.optJSONObject("color") ?: JSONObject()
            val colorConfig = ColorFilterConfig(
                preset = enumOrDefault(colorJson.optString("preset"), ColorFilterPreset.NONE),
                brightness = colorJson.optDouble("brightness", 0.0).toFloat().coerceIn(-0.5f, 0.5f),
                contrast = colorJson.optDouble("contrast", 1.0).toFloat().coerceIn(0.5f, 1.5f),
                saturation = colorJson.optDouble("saturation", 1.0).toFloat().coerceIn(0f, 2f)
            )

            val clips = mutableListOf<VideoClip>()
            val clipsArr = json.optJSONArray("clips") ?: JSONArray()
            for (i in 0 until clipsArr.length()) {
                val c = clipsArr.getJSONObject(i)
                val uriStr = c.optString("uri")
                if (uriStr.isNotBlank()) {
                    val rawOriginalDur = c.optLong("originalDurationMs", 0L).coerceAtLeast(0L)
                    val rawTrimStart = c.optLong("trimStartMs", 0L).coerceIn(0L, rawOriginalDur)
                    val rawTrimEnd = c.optLong("trimEndMs", rawOriginalDur)
                        .coerceIn(rawTrimStart, rawOriginalDur)

                    clips.add(
                        VideoClip(
                            id = c.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                            uri = Uri.parse(uriStr),
                            displayName = c.optString("displayName", "Clip_${i + 1}"),
                            originalDurationMs = rawOriginalDur,
                            trimStartMs = rawTrimStart,
                            trimEndMs = rawTrimEnd,
                            loopCount = c.optInt("loopCount", 1).coerceIn(1, 20),
                            speed = c.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 4.0f),
                            rotationDegrees = c.optInt("rotationDegrees", 0),
                            isMirrored = c.optBoolean("isMirrored", false),
                            width = c.optInt("width", 1080).coerceAtLeast(1),
                            height = c.optInt("height", 1920).coerceAtLeast(1),
                            isImage = c.optBoolean("isImage", false),
                            mimeType = c.optString("mimeType").ifBlank { null },
                            motionEffect = enumOrDefault(c.optString("motionEffect"), CameraMotionEffect.NONE),
                            motionIntensity = c.optDouble("motionIntensity", 0.20)
                                .toFloat()
                                .coerceIn(0.05f, 0.50f)
                        )
                    )
                }
            }

            val audioJson = json.optJSONObject("audio") ?: JSONObject()
            val audioUriStr = audioJson.optString("uri")
            val beats = mutableListOf<Long>()
            val beatsArr = audioJson.optJSONArray("beatMarkersMs") ?: JSONArray()
            for (i in 0 until beatsArr.length()) {
                beats.add(beatsArr.getLong(i))
            }

            val parsedAudioUri = if (audioUriStr.isNotBlank()) Uri.parse(audioUriStr) else null
            val peaks = if (parsedAudioUri != null) {
                AudioWaveformExtractor.extractWaveformPeaks(context, parsedAudioUri)
            } else {
                emptyList()
            }

            val rawTotalDur = audioJson.optLong("totalDurationMs", 0L).coerceAtLeast(0L)
            val rawStartOffset = audioJson.optLong("startOffsetMs", 0L)
                .coerceIn(0L, rawTotalDur)

            val audioConfig = AudioTrackConfig(
                uri = parsedAudioUri,
                displayName = audioJson.optString("displayName", ""),
                isExtractedFromVideo = audioJson.optBoolean("isExtractedFromVideo", false),
                totalDurationMs = rawTotalDur,
                startOffsetMs = rawStartOffset,
                isOriginalAudioMuted = audioJson.optBoolean("isOriginalAudioMuted", true),
                originalVideoVolume = audioJson.optDouble("originalVideoVolume", 0.0)
                    .toFloat()
                    .coerceIn(0f, 2f),
                musicVolume = audioJson.optDouble("musicVolume", 1.0)
                    .toFloat()
                    .coerceIn(0f, 2f),
                fadeInDurationMs = audioJson.optLong("fadeInDurationMs", 0L).coerceAtLeast(0L),
                fadeOutDurationMs = audioJson.optLong("fadeOutDurationMs", 0L).coerceAtLeast(0L),
                waveformPeaks = peaks,
                beatMarkersMs = beats
            )

            val subtitles = mutableListOf<SubtitleItem>()
            val subsArr = json.optJSONArray("subtitles") ?: JSONArray()
            for (i in 0 until subsArr.length()) {
                val s = subsArr.getJSONObject(i)
                val rawStart = s.optLong("startMs", 0L).coerceAtLeast(0L)
                val rawEnd = s.optLong("endMs", rawStart + 1000L)
                    .coerceAtLeast(rawStart + 100L)

                subtitles.add(
                    SubtitleItem(
                        id = s.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
                        text = s.optString("text"),
                        startMs = rawStart,
                        endMs = rawEnd,
                        fontFamily = enumOrDefault(s.optString("fontFamily"), SubtitleFontFamily.SANS_SERIF),
                        fontSizeSp = s.optDouble("fontSizeSp", 22.0).toFloat().coerceIn(8f, 72f),
                        textColorHex = s.optLong("textColorHex", 0xFFFFFFFF),
                        hasDarkBackground = s.optBoolean("hasDarkBackground", true),
                        position = enumOrDefault(s.optString("position"), SubtitlePosition.BOTTOM)
                    )
                )
            }

            val wmJson = json.optJSONObject("watermark") ?: JSONObject()
            val wmUriStr = wmJson.optString("uri")
            val watermarkConfig = WatermarkConfig(
                uri = if (wmUriStr.isNotBlank()) Uri.parse(wmUriStr) else null,
                displayName = wmJson.optString("displayName", ""),
                scalePercent = wmJson.optDouble("scalePercent", 0.22)
                    .toFloat()
                    .coerceIn(0.10f, 0.50f),
                opacity = wmJson.optDouble("opacity", 0.85)
                    .toFloat()
                    .coerceIn(0.15f, 1.0f),
                position = enumOrDefault(wmJson.optString("position"), WatermarkPosition.TOP_RIGHT)
            )

            EditorUiState(
                projectName = projectName,
                clips = clips,
                selectedClipId = clips.firstOrNull()?.id,
                transitionConfig = transitionConfig,
                colorConfig = colorConfig,
                audioConfig = audioConfig,
                aspectRatio = aspectRatio,
                cropScaleMode = cropScaleMode,
                subtitles = subtitles,
                watermarkConfig = watermarkConfig
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteDraft(context: Context, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getDraftsDir(context), fileName)
        if (file.exists()) file.delete() else false
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T {
        return try {
            if (name.isBlank()) default else enumValueOf<T>(name)
        } catch (_: Exception) {
            default
        }
    }
}
