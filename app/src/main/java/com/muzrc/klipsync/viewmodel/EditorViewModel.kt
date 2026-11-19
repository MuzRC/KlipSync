package com.muzrc.klipsync.viewmodel

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.muzrc.klipsync.media.MediaMetadataReader
import com.muzrc.klipsync.media.VideoExporter
import com.muzrc.klipsync.model.AspectRatioPreset
import com.muzrc.klipsync.model.ColorFilterPreset
import com.muzrc.klipsync.model.CropScaleMode
import com.muzrc.klipsync.model.EditorTab
import com.muzrc.klipsync.model.EditorUiState
import com.muzrc.klipsync.model.ExportFrameRate
import com.muzrc.klipsync.model.ExportResolution
import com.muzrc.klipsync.model.ExportState
import com.muzrc.klipsync.model.JoinMode
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.TransitionType
import com.muzrc.klipsync.model.UpdateCheckState
import com.muzrc.klipsync.model.VideoEndBehavior
import com.muzrc.klipsync.model.WatermarkPosition
import com.muzrc.klipsync.preview.PreviewPlayerController
import com.muzrc.klipsync.util.DraftStorageManager
import com.muzrc.klipsync.util.SavedDraftSummary
import com.muzrc.klipsync.util.SubtitleParser
import com.muzrc.klipsync.util.UpdateChecker
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val metadataReader = MediaMetadataReader(appContext)
    private val videoExporter = VideoExporter(appContext)
    val playerController = PreviewPlayerController(appContext)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _drafts = MutableStateFlow<List<SavedDraftSummary>>(emptyList())
    val drafts: StateFlow<List<SavedDraftSummary>> = _drafts.asStateFlow()

    private var playbackTickerJob: Job? = null
    private var exportJob: Job? = null

    init {
        startPlaybackTicker()
        refreshDrafts()
    }

    private fun startPlaybackTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                val totalDur = state.finalTimelineDurationMs
                if (state.isPlaying && totalDur > 0L) {
                    val pos = playerController.tickSync(true, totalDur)
                    if (pos >= totalDur - 40L) {
                        playerController.pause()
                        _uiState.update {
                            it.copy(isPlaying = false, currentPositionMs = totalDur)
                        }
                    } else {
                        _uiState.update { it.copy(currentPositionMs = pos) }
                    }
                }
                delay(55L)
            }
        }
    }

    private fun syncPreviewWithState(newState: EditorUiState) {
        playerController.syncMedia(
            clips = newState.clips,
            transitionConfig = newState.transitionConfig,
            audio = newState.audioConfig
        )
    }

    fun selectTab(tab: EditorTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectClip(clipId: String) {
        _uiState.update { it.copy(selectedClipId = clipId) }
    }

    // --- Pengurusan Klip Video ---
    fun addVideoClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pausePlayback()
        viewModelScope.launch {
            val newClips = uris.map { uri ->
                DraftStorageManager.takePersistablePermissionIfPossible(appContext, uri)
                metadataReader.readVideoClip(uri)
            }
            _uiState.update { state ->
                val merged = state.clips + newClips
                val activeId = state.selectedClipId ?: newClips.firstOrNull()?.id
                val updated = state.copy(clips = merged, selectedClipId = activeId)
                syncPreviewWithState(updated)
                updated
            }
        }
    }

    fun removeClip(clipId: String) {
        pausePlayback()
        _uiState.update { state ->
            val remaining = state.clips.filterNot { it.id == clipId }
            val nextSelected = if (state.selectedClipId == clipId) {
                remaining.firstOrNull()?.id
            } else {
                state.selectedClipId
            }
            val updated = state.copy(
                clips = remaining,
                selectedClipId = nextSelected,
                currentPositionMs = 0L
            )
            syncPreviewWithState(updated)
            playerController.seekTo(0L)
            updated
        }
    }

    fun moveClipUp(index: Int) {
        if (index <= 0) return
        pausePlayback()
        _uiState.update { state ->
            val mutable = state.clips.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index - 1, item)
            val updated = state.copy(clips = mutable)
            syncPreviewWithState(updated)
            updated
        }
    }

    fun moveClipDown(index: Int) {
        val currentClips = _uiState.value.clips
        if (index < 0 || index >= currentClips.lastIndex) return
        pausePlayback()
        _uiState.update { state ->
            val mutable = state.clips.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index + 1, item)
            val updated = state.copy(clips = mutable)
            syncPreviewWithState(updated)
            updated
        }
    }

    fun updateClipTrim(clipId: String, startMs: Long, endMs: Long) {
        pausePlayback()
        _uiState.update { state ->
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) {
                    val safeStart = startMs.coerceIn(0L, clip.originalDurationMs)
                    val safeEnd = endMs.coerceIn(safeStart + 200L, clip.originalDurationMs)
                    clip.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
                } else {
                    clip
                }
            }
            val updated = state.copy(clips = updatedClips, currentPositionMs = 0L)
            syncPreviewWithState(updated)
            playerController.seekTo(0L)
            updated
        }
    }

    fun setClipLoopCount(clipId: String, loopCount: Int) {
        pausePlayback()
        _uiState.update { state ->
            val safeLoop = loopCount.coerceIn(1, 20)
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) clip.copy(loopCount = safeLoop) else clip
            }
            // Aktifkan transisi Crossfade secara automatik jika loop > 1 dan transisi masih NONE
            val nextTransition = if (safeLoop > 1 && state.transitionConfig.transitionType == TransitionType.NONE) {
                state.transitionConfig.copy(transitionType = TransitionType.CROSSFADE)
            } else {
                state.transitionConfig
            }
            val updated = state.copy(clips = updatedClips, transitionConfig = nextTransition)
            syncPreviewWithState(updated)
            updated
        }
    }

    fun setClipSpeed(clipId: String, speed: Float) {
        pausePlayback()
        _uiState.update { state ->
            val safeSpeed = speed.coerceIn(0.25f, 3.0f)
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) clip.copy(speed = safeSpeed) else clip
            }
            val updated = state.copy(clips = updatedClips)
            syncPreviewWithState(updated)
            updated
        }
    }

    fun applySpeedToAllClips(speed: Float) {
        pausePlayback()
        _uiState.update { state ->
            val safeSpeed = speed.coerceIn(0.25f, 3.0f)
            val updatedClips = state.clips.map { it.copy(speed = safeSpeed) }
            val updated = state.copy(clips = updatedClips)
            syncPreviewWithState(updated)
            updated
        }
    }

    fun rotateClip90(clipId: String) {
        _uiState.update { state ->
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) {
                    clip.copy(rotationDegrees = (clip.rotationDegrees + 90) % 360)
                } else {
                    clip
                }
            }
            state.copy(clips = updatedClips)
        }
    }

    fun toggleClipMirror(clipId: String) {
        _uiState.update { state ->
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) clip.copy(isMirrored = !clip.isMirrored) else clip
            }
            state.copy(clips = updatedClips)
        }
    }

    fun cutClipAtTimelinePosition(clipId: String, timelineTargetMs: Long) {
        pausePlayback()
        _uiState.update { state ->
            val targetIndex = state.clips.indexOfFirst { it.id == clipId }
            if (targetIndex == -1) return@update state

            var startInTimeline = 0L
            for (i in 0 until targetIndex) {
                startInTimeline += state.clips[i].effectiveDurationMs
            }

            val targetClip = state.clips[targetIndex]
            val singlePlayedDur = targetClip.singlePlayDurationMs
            if (singlePlayedDur < 500L) return@update state

            val localPlayedOffset = timelineTargetMs - startInTimeline
            val safePlayedOffset = if (localPlayedOffset in 250L..(singlePlayedDur - 250L)) {
                localPlayedOffset
            } else {
                singlePlayedDur / 2L
            }

            val rawOffset = (safePlayedOffset * targetClip.speed).toLong()
            val splitPointMs = (targetClip.trimStartMs + rawOffset)
                .coerceIn(targetClip.trimStartMs + 200L, targetClip.trimEndMs - 200L)

            val baseName = targetClip.displayName.substringBeforeLast('.')
            val partA = targetClip.copy(
                id = UUID.randomUUID().toString(),
                displayName = "$baseName (A)",
                trimEndMs = splitPointMs,
                loopCount = 1
            )
            val partB = targetClip.copy(
                id = UUID.randomUUID().toString(),
                displayName = "$baseName (B)",
                trimStartMs = splitPointMs,
                loopCount = 1
            )

            val mutable = state.clips.toMutableList()
            mutable.removeAt(targetIndex)
            mutable.add(targetIndex, partB)
            mutable.add(targetIndex, partA)

            val updated = state.copy(clips = mutable, selectedClipId = partA.id)
            syncPreviewWithState(updated)
            updated
        }
    }

    fun setJoinMode(mode: JoinMode) {
        pausePlayback()
        _uiState.update { state ->
            val updated = state.copy(
                transitionConfig = state.transitionConfig.copy(joinMode = mode)
            )
            syncPreviewWithState(updated)
            updated
        }
    }

    fun setTransitionType(type: TransitionType) {
        _uiState.update { state ->
            state.copy(
                transitionConfig = state.transitionConfig.copy(transitionType = type)
            )
        }
    }

    fun setTransitionDuration(durationMs: Long) {
        pausePlayback()
        _uiState.update { state ->
            val updated = state.copy(
                transitionConfig = state.transitionConfig.copy(durationMs = durationMs)
            )
            syncPreviewWithState(updated)
            updated
        }
    }

    fun setVideoEndBehavior(behavior: VideoEndBehavior) {
        pausePlayback()
        _uiState.update { state ->
            // Aktifkan transisi Crossfade secara automatik apabila pilih Auto-Loop Video supaya cut tidak ketara
            val nextTransition = if (
                behavior == VideoEndBehavior.LOOP_TO_AUDIO &&
                state.transitionConfig.transitionType == TransitionType.NONE
            ) {
                state.transitionConfig.copy(
                    endBehavior = behavior,
                    transitionType = TransitionType.CROSSFADE
                )
            } else {
                state.transitionConfig.copy(endBehavior = behavior)
            }
            val updated = state.copy(transitionConfig = nextTransition)
            syncPreviewWithState(updated)
            updated
        }
    }

    // --- Penapis Warna & Pencahayaan ---
    fun setColorPreset(preset: ColorFilterPreset) {
        _uiState.update { state ->
            state.copy(colorConfig = state.colorConfig.copy(preset = preset))
        }
    }

    fun setBrightness(value: Float) {
        _uiState.update { state ->
            state.copy(colorConfig = state.colorConfig.copy(brightness = value.coerceIn(-0.4f, 0.4f)))
        }
    }

    fun setContrast(value: Float) {
        _uiState.update { state ->
            state.copy(colorConfig = state.colorConfig.copy(contrast = value.coerceIn(0.6f, 1.5f)))
        }
    }

    fun setSaturation(value: Float) {
        _uiState.update { state ->
            state.copy(colorConfig = state.colorConfig.copy(saturation = value.coerceIn(0f, 1.8f)))
        }
    }

    fun resetColorConfig() {
        _uiState.update { state ->
            state.copy(
                colorConfig = state.colorConfig.copy(
                    preset = ColorFilterPreset.NONE,
                    brightness = 0f,
                    contrast = 1f,
                    saturation = 1f
                )
            )
        }
    }

    // --- Pengurusan Audio & Beat Markers ---
    fun selectAudioTrack(uri: Uri, extractedFromVideo: Boolean = false) {
        pausePlayback()
        DraftStorageManager.takePersistablePermissionIfPossible(appContext, uri)
        viewModelScope.launch {
            val audioTrack = metadataReader.readAudioTrack(uri, extractedFromVideo)
            _uiState.update { state ->
                val preservedConfig = audioTrack.copy(
                    isOriginalAudioMuted = state.audioConfig.isOriginalAudioMuted,
                    originalVideoVolume = state.audioConfig.originalVideoVolume,
                    musicVolume = state.audioConfig.musicVolume,
                    fadeInDurationMs = state.audioConfig.fadeInDurationMs,
                    fadeOutDurationMs = state.audioConfig.fadeOutDurationMs
                )
                val updated = state.copy(audioConfig = preservedConfig, currentPositionMs = 0L)
                syncPreviewWithState(updated)
                playerController.seekTo(0L)
                updated
            }
        }
    }

    fun removeAudioTrack() {
        pausePlayback()
        _uiState.update { state ->
            val updated = state.copy(
                audioConfig = state.audioConfig.copy(
                    uri = null,
                    displayName = "",
                    isExtractedFromVideo = false,
                    totalDurationMs = 0L,
                    startOffsetMs = 0L,
                    waveformPeaks = emptyList(),
                    beatMarkersMs = emptyList()
                ),
                currentPositionMs = 0L
            )
            syncPreviewWithState(updated)
            playerController.seekTo(0L)
            updated
        }
    }

    fun setAudioStartOffset(offsetMs: Long) {
        pausePlayback()
        _uiState.update { state ->
            val maxOffset = (state.audioConfig.totalDurationMs - 500L).coerceAtLeast(0L)
            val safeOffset = offsetMs.coerceIn(0L, maxOffset)
            val updated = state.copy(
                audioConfig = state.audioConfig.copy(startOffsetMs = safeOffset),
                currentPositionMs = 0L
            )
            syncPreviewWithState(updated)
            playerController.seekTo(0L)
            updated
        }
    }

    fun toggleMuteOriginalAudio() {
        _uiState.update { state ->
            val nextMute = !state.audioConfig.isOriginalAudioMuted
            val nextVol = if (nextMute) 0f else {
                if (state.audioConfig.originalVideoVolume <= 0.01f) 0.5f
                else state.audioConfig.originalVideoVolume
            }
            val updated = state.copy(
                audioConfig = state.audioConfig.copy(
                    isOriginalAudioMuted = nextMute,
                    originalVideoVolume = nextVol
                )
            )
            syncPreviewWithState(updated)
            updated
        }
    }

    fun setOriginalVideoVolume(volume: Float) {
        _uiState.update { state ->
            val clamped = volume.coerceIn(0f, 1f)
            val updated = state.copy(
                audioConfig = state.audioConfig.copy(
                    isOriginalAudioMuted = clamped <= 0.01f,
                    originalVideoVolume = clamped
                )
            )
            syncPreviewWithState(updated)
            updated
        }
    }

    fun setMusicVolume(volume: Float) {
        _uiState.update { state ->
            val updated = state.copy(
                audioConfig = state.audioConfig.copy(musicVolume = volume.coerceIn(0f, 1f))
            )
            syncPreviewWithState(updated)
            updated
        }
    }

    fun setFadeInDuration(fadeInMs: Long) {
        _uiState.update { state ->
            val updated = state.copy(
                audioConfig = state.audioConfig.copy(fadeInDurationMs = fadeInMs)
            )
            syncPreviewWithState(updated)
            updated
        }
    }

    fun setFadeOutDuration(fadeOutMs: Long) {
        _uiState.update { state ->
            val updated = state.copy(
                audioConfig = state.audioConfig.copy(fadeOutDurationMs = fadeOutMs)
            )
            syncPreviewWithState(updated)
            updated
        }
    }

    fun addBeatMarkerAtCurrentPosition() {
        _uiState.update { state ->
            val pos = state.currentPositionMs
            val existing = state.audioConfig.beatMarkersMs
            if (existing.any { kotlin.math.abs(it - pos) < 120L }) return@update state
            val updatedBeats = (existing + pos).sorted()
            state.copy(audioConfig = state.audioConfig.copy(beatMarkersMs = updatedBeats))
        }
    }

    fun removeBeatMarker(markerMs: Long) {
        _uiState.update { state ->
            val updatedBeats = state.audioConfig.beatMarkersMs.filterNot { it == markerMs }
            state.copy(audioConfig = state.audioConfig.copy(beatMarkersMs = updatedBeats))
        }
    }

    fun clearBeatMarkers() {
        _uiState.update { state ->
            state.copy(audioConfig = state.audioConfig.copy(beatMarkersMs = emptyList()))
        }
    }

    fun cutClipAtBeat(beatMs: Long) {
        val state = _uiState.value
        if (state.clips.isEmpty()) return

        var acc = 0L
        for (clip in state.clips) {
            val dur = clip.effectiveDurationMs
            if (beatMs in (acc + 250L)..(acc + dur - 250L)) {
                cutClipAtTimelinePosition(clip.id, beatMs)
                return
            }
            acc += dur
        }
    }

    // --- Crop & Nisbah Skrin ---
    fun setAspectRatio(preset: AspectRatioPreset) {
        _uiState.update { it.copy(aspectRatio = preset) }
    }

    fun setCropScaleMode(mode: CropScaleMode) {
        _uiState.update { it.copy(cropScaleMode = mode) }
    }

    // --- Sarikata & Import Fail Lirik (.LRC / .SRT) ---
    fun upsertSubtitle(item: SubtitleItem) {
        _uiState.update { state ->
            val exists = state.subtitles.any { it.id == item.id }
            val updatedList = if (exists) {
                state.subtitles.map { if (it.id == item.id) item else it }
            } else {
                state.subtitles + item
            }.sortedBy { it.startMs }
            state.copy(subtitles = updatedList)
        }
    }

    fun removeSubtitle(subtitleId: String) {
        _uiState.update { state ->
            state.copy(subtitles = state.subtitles.filterNot { it.id == subtitleId })
        }
    }

    fun clearAllSubtitles() {
        _uiState.update { it.copy(subtitles = emptyList()) }
    }

    fun importSubtitlesFromUri(uri: Uri) {
        viewModelScope.launch {
            val maxDur = _uiState.value.finalTimelineDurationMs.coerceAtLeast(180_000L)
            val imported = SubtitleParser.parseFromUri(appContext, uri, maxDur)
            if (imported.isNotEmpty()) {
                _uiState.update { state ->
                    val combined = (state.subtitles + imported).sortedBy { it.startMs }
                    state.copy(subtitles = combined)
                }
            }
        }
    }

    // --- Logo / Watermark PNG ---
    fun selectWatermarkImage(uri: Uri) {
        DraftStorageManager.takePersistablePermissionIfPossible(appContext, uri)
        val name = metadataReader.resolveDisplayName(uri) ?: "Logo.png"
        _uiState.update { state ->
            state.copy(
                watermarkConfig = state.watermarkConfig.copy(
                    uri = uri,
                    displayName = name
                )
            )
        }
    }

    fun removeWatermark() {
        _uiState.update { state ->
            state.copy(
                watermarkConfig = state.watermarkConfig.copy(
                    uri = null,
                    displayName = ""
                )
            )
        }
    }

    fun setWatermarkScale(scale: Float) {
        _uiState.update { state ->
            state.copy(
                watermarkConfig = state.watermarkConfig.copy(
                    scalePercent = scale.coerceIn(0.08f, 0.55f)
                )
            )
        }
    }

    fun setWatermarkOpacity(opacity: Float) {
        _uiState.update { state ->
            state.copy(
                watermarkConfig = state.watermarkConfig.copy(
                    opacity = opacity.coerceIn(0.15f, 1.0f)
                )
            )
        }
    }

    fun setWatermarkPosition(position: WatermarkPosition) {
        _uiState.update { state ->
            state.copy(
                watermarkConfig = state.watermarkConfig.copy(position = position)
            )
        }
    }

    // --- Kawalan Pratonton ---
    fun togglePlayPause() {
        val state = _uiState.value
        if (state.finalTimelineDurationMs <= 0L) return

        if (state.isPlaying) {
            pausePlayback()
        } else {
            playerController.play(state.finalTimelineDurationMs)
            _uiState.update { it.copy(isPlaying = true) }
        }
    }

    fun pausePlayback() {
        playerController.pause()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(positionMs: Long) {
        val total = _uiState.value.finalTimelineDurationMs
        val safePos = positionMs.coerceIn(0L, total)
        playerController.seekTo(safePos)
        _uiState.update { it.copy(currentPositionMs = safePos) }
    }

    // --- Draf Projek Tempatan ---
    fun refreshDrafts() {
        viewModelScope.launch {
            _drafts.value = DraftStorageManager.listDrafts(appContext)
        }
    }

    fun saveCurrentDraft(projectName: String) {
        pausePlayback()
        viewModelScope.launch {
            val snapshot = _uiState.value.copy(projectName = projectName)
            DraftStorageManager.saveDraft(appContext, snapshot, projectName)
            _uiState.update { it.copy(projectName = projectName) }
            refreshDrafts()
        }
    }

    fun loadDraft(fileName: String) {
        pausePlayback()
        viewModelScope.launch {
            val loadedState = DraftStorageManager.loadDraft(appContext, fileName)
            if (loadedState != null) {
                _uiState.value = loadedState
                syncPreviewWithState(loadedState)
                playerController.seekTo(0L)
            }
        }
    }

    fun deleteDraft(fileName: String) {
        viewModelScope.launch {
            DraftStorageManager.deleteDraft(appContext, fileName)
            refreshDrafts()
        }
    }

    // --- Eksport Video ---
    fun setExportResolution(resolution: ExportResolution) {
        _uiState.update { state ->
            state.copy(exportConfig = state.exportConfig.copy(resolution = resolution))
        }
    }

    fun setExportFrameRate(frameRate: ExportFrameRate) {
        _uiState.update { state ->
            state.copy(exportConfig = state.exportConfig.copy(frameRate = frameRate))
        }
    }

    fun startExport() {
        pausePlayback()
        exportJob?.cancel()
        val snapshot = _uiState.value
        exportJob = viewModelScope.launch {
            videoExporter.exportProject(snapshot).collect { exportState ->
                _uiState.update { it.copy(exportState = exportState) }
            }
        }
    }

    fun cancelOrResetExport() {
        exportJob?.cancel()
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    // --- Semak Kemas Kini Manual ---
    fun checkForUpdatesManual() {
        viewModelScope.launch {
            _uiState.update { it.copy(updateCheckState = UpdateCheckState.Checking) }
            val result = UpdateChecker.checkForUpdates()
            _uiState.update { it.copy(updateCheckState = result) }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(updateCheckState = UpdateCheckState.Idle) }
    }

    override fun onCleared() {
        playbackTickerJob?.cancel()
        exportJob?.cancel()
        playerController.release()
        super.onCleared()
    }
}
