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

    fun takePersistablePermissionIfPossible(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
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
                clipsArr.put(JSONObject().apply {
                    put("id", clip.id)
                    put("uri", clip.uri.toString())
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
                })
            }
            put("clips", clipsArr)

            val audio = state.audioConfig
            audio.uri?.let { takePersistablePermissionIfPossible(context, it) }
            put("audio", JSONObject().apply {
                put("uri", audio.uri?.toString() ?: "")
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
            wm.uri?.let { takePersistablePermissionIfPossible(context, it) }
            put("watermark", JSONObject().apply {
                put("uri", wm.uri?.toString() ?: "")
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
                durationMs = transJson.optLong("durationMs", 800L),
                endBehavior = enumOrDefault(transJson.optString("endBehavior"), VideoEndBehavior.FREEZE_FRAME)
            )

            val colorJson = json.optJSONObject("color") ?: JSONObject()
            val colorConfig = ColorFilterConfig(
                preset = enumOrDefault(colorJson.optString("preset"), ColorFilterPreset.NONE),
                brightness = colorJson.optDouble("brightness", 0.0).toFloat(),
                contrast = colorJson.optDouble("contrast", 1.0).toFloat(),
                saturation = colorJson.optDouble("saturation", 1.0).toFloat()
            )

            val clips = mutableListOf<VideoClip>()
            val clipsArr = json.optJSONArray("clips") ?: JSONArray()
            for (i in 0 until clipsArr.length()) {
                val c = clipsArr.getJSONObject(i)
                val uriStr = c.optString("uri")
                if (uriStr.isNotBlank()) {
                    clips.add(
                        VideoClip(
                            id = c.optString("id"),
                            uri = Uri.parse(uriStr),
                            displayName = c.optString("displayName", "Clip_${i + 1}"),
                            originalDurationMs = c.optLong("originalDurationMs", 0L),
                            trimStartMs = c.optLong("trimStartMs", 0L),
                            trimEndMs = c.optLong("trimEndMs", 0L),
                            loopCount = c.optInt("loopCount", 1),
                            speed = c.optDouble("speed", 1.0).toFloat(),
                            rotationDegrees = c.optInt("rotationDegrees", 0),
                            isMirrored = c.optBoolean("isMirrored", false),
                            width = c.optInt("width", 1080),
                            height = c.optInt("height", 1920),
                            isImage = c.optBoolean("isImage", false),
                            mimeType = c.optString("mimeType").ifBlank { null },
                            motionEffect = enumOrDefault(c.optString("motionEffect"), CameraMotionEffect.NONE)
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

            val audioConfig = AudioTrackConfig(
                uri = parsedAudioUri,
                displayName = audioJson.optString("displayName", ""),
                isExtractedFromVideo = audioJson.optBoolean("isExtractedFromVideo", false),
                totalDurationMs = audioJson.optLong("totalDurationMs", 0L),
                startOffsetMs = audioJson.optLong("startOffsetMs", 0L),
                isOriginalAudioMuted = audioJson.optBoolean("isOriginalAudioMuted", true),
                originalVideoVolume = audioJson.optDouble("originalVideoVolume", 0.0).toFloat(),
                musicVolume = audioJson.optDouble("musicVolume", 1.0).toFloat(),
                fadeInDurationMs = audioJson.optLong("fadeInDurationMs", 0L),
                fadeOutDurationMs = audioJson.optLong("fadeOutDurationMs", 0L),
                waveformPeaks = peaks,
                beatMarkersMs = beats
            )

            val subtitles = mutableListOf<SubtitleItem>()
            val subsArr = json.optJSONArray("subtitles") ?: JSONArray()
            for (i in 0 until subsArr.length()) {
                val s = subsArr.getJSONObject(i)
                subtitles.add(
                    SubtitleItem(
                        id = s.optString("id"),
                        text = s.optString("text"),
                        startMs = s.optLong("startMs", 0L),
                        endMs = s.optLong("endMs", 1000L),
                        fontFamily = enumOrDefault(s.optString("fontFamily"), SubtitleFontFamily.SANS_SERIF),
                        fontSizeSp = s.optDouble("fontSizeSp", 22.0).toFloat(),
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
                scalePercent = wmJson.optDouble("scalePercent", 0.22).toFloat(),
                opacity = wmJson.optDouble("opacity", 0.85).toFloat(),
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
