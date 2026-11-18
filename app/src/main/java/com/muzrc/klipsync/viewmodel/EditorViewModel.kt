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
import com.muzrc.klipsync.model.EditorTab
import com.muzrc.klipsync.model.EditorUiState
import com.muzrc.klipsync.model.ExportFrameRate
import com.muzrc.klipsync.model.ExportResolution
import com.muzrc.klipsync.model.ExportState
import com.muzrc.klipsync.model.JoinMode
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.TransitionType
import com.muzrc.klipsync.model.UpdateCheckState
import com.muzrc.klipsync.preview.PreviewPlayerController
import com.muzrc.klipsync.util.UpdateChecker
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

    private val metadataReader = MediaMetadataReader(application)
    private val videoExporter = VideoExporter(application)
    val playerController = PreviewPlayerController(application)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var playbackTickerJob: Job? = null
    private var exportJob: Job? = null

    init {
        startPlaybackTicker()
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
                delay(60L)
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

    // --- Pengurusan Klip Video ---
    fun addVideoClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pausePlayback()
        viewModelScope.launch {
            val newClips = uris.map { uri -> metadataReader.readVideoClip(uri) }
            _uiState.update { state ->
                val updated = state.copy(clips = state.clips + newClips)
                syncPreviewWithState(updated)
                updated
            }
        }
    }

    fun removeClip(clipId: String) {
        pausePlayback()
        _uiState.update { state ->
            val updated = state.copy(
                clips = state.clips.filterNot { it.id == clipId },
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

    // --- Pengurusan Audio ---
    fun selectAudioTrack(uri: Uri) {
        pausePlayback()
        viewModelScope.launch {
            val audioTrack = metadataReader.readAudioTrack(uri)
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
                    totalDurationMs = 0L,
                    startOffsetMs = 0L
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

    // --- Crop Nisbah Skrin ---
    fun setAspectRatio(preset: AspectRatioPreset) {
        _uiState.update { it.copy(aspectRatio = preset) }
    }

    // --- Sarikata Manual ---
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
