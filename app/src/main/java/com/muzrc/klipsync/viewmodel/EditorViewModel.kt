package com.muzrc.klipsync.viewmodel

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.muzrc.klipsync.media.BeatDetector
import com.muzrc.klipsync.media.BeatSensitivity
import com.muzrc.klipsync.media.MediaMetadataReader
import com.muzrc.klipsync.media.VideoExporter
import com.muzrc.klipsync.model.AspectRatioPreset
import com.muzrc.klipsync.model.CameraMotionEffect
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
import com.muzrc.klipsync.model.VideoClip
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

    // Sejarah Undo / Redo (maksimum 25 langkah)
    private val undoStack = ArrayDeque<EditorUiState>()
    private val redoStack = ArrayDeque<EditorUiState>()
    private val maxHistorySteps = 25
    private var lastContinuousActionKey: String = ""
    private var lastContinuousActionMs: Long = 0L

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

    private fun cleanSnapshotForHistory(state: EditorUiState): EditorUiState {
        return state.copy(
            isPlaying = false,
            isDetectingBeats = false,
            exportState = ExportState.Idle,
            updateCheckState = UpdateCheckState.Idle,
            canUndo = false,
            canRedo = false
        )
    }

    /**
     * Mengemas kini keadaan penyuntingan sambil merekodkan langkah ke dalam sejarah Undo/Redo
     * dan menyelaraskan Live Preview secara automatik.
     */
    private fun updateProjectState(
        continuousKey: String? = null,
        syncPreview: Boolean = true,
        seekAfterSyncMs: Long? = null,
        transform: (EditorUiState) -> EditorUiState
    ) {
        _uiState.update { current ->
            val now = SystemClock.elapsedRealtime()
            val shouldRecordSnapshot = if (continuousKey != null) {
                val isSameRecentDrag = (continuousKey == lastContinuousActionKey) &&
                    ((now - lastContinuousActionMs) < 1100L)
                lastContinuousActionKey = continuousKey
                lastContinuousActionMs = now
                !isSameRecentDrag
            } else {
                lastContinuousActionKey = ""
                lastContinuousActionMs = 0L
                true
            }

            if (shouldRecordSnapshot) {
                undoStack.addLast(cleanSnapshotForHistory(current))
                while (undoStack.size > maxHistorySteps) {
                    undoStack.removeFirst()
                }
                redoStack.clear()
            }

            val transformed = transform(current)
            val clampedPos = (seekAfterSyncMs ?: transformed.currentPositionMs)
                .coerceIn(0L, transformed.finalTimelineDurationMs)

            val finalState = transformed.copy(
                currentPositionMs = clampedPos,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )

            if (syncPreview) {
                syncPreviewWithState(finalState)
            }
            if (seekAfterSyncMs != null || clampedPos != current.currentPositionMs) {
                playerController.seekTo(clampedPos)
            }
            finalState
        }
    }

    // --- Projek Baharu, Undo & Redo ---
    fun createNewProject() {
        pausePlayback()
        exportJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        lastContinuousActionKey = ""
        lastContinuousActionMs = 0L

        val freshState = EditorUiState()
        _uiState.value = freshState
        syncPreviewWithState(freshState)
        playerController.seekTo(0L)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        pausePlayback()
        lastContinuousActionKey = ""

        val current = _uiState.value
        redoStack.addLast(cleanSnapshotForHistory(current))

        val previous = undoStack.removeLast()
        val safePos = previous.currentPositionMs.coerceIn(0L, previous.finalTimelineDurationMs)
        val restored = previous.copy(
            isPlaying = false,
            currentPositionMs = safePos,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )

        _uiState.value = restored
        syncPreviewWithState(restored)
        playerController.seekTo(safePos)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        pausePlayback()
        lastContinuousActionKey = ""

        val current = _uiState.value
        undoStack.addLast(cleanSnapshotForHistory(current))

        val next = redoStack.removeLast()
        val safePos = next.currentPositionMs.coerceIn(0L, next.finalTimelineDurationMs)
        val restored = next.copy(
            isPlaying = false,
            currentPositionMs = safePos,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty()
        )

        _uiState.value = restored
        syncPreviewWithState(restored)
        playerController.seekTo(safePos)
    }

    fun selectTab(tab: EditorTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectClip(clipId: String) {
        val state = _uiState.value
        val targetIndex = state.clips.indexOfFirst { it.id == clipId }
        if (targetIndex == -1) {
            _uiState.update { it.copy(selectedClipId = clipId) }
            return
        }

        var targetStartMs = 0L
        val overlapPerJoin = if (state.transitionConfig.joinMode == JoinMode.OVERLAP) {
            state.transitionConfig.durationMs.coerceAtLeast(0L)
        } else {
            0L
        }

        for (i in 0 until targetIndex) {
            targetStartMs += state.clips[i].effectiveDurationMs
            if (overlapPerJoin > 0L) {
                targetStartMs = (targetStartMs - overlapPerJoin).coerceAtLeast(0L)
            }
        }

        pausePlayback()
        val safePos = targetStartMs.coerceIn(0L, state.finalTimelineDurationMs)
        playerController.seekTo(safePos)
        _uiState.update {
            it.copy(
                selectedClipId = clipId,
                currentPositionMs = safePos
            )
        }
    }

    // --- Pengurusan Klip Video & Gambar ---
    fun addVideoClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pausePlayback()
        viewModelScope.launch {
            val newClips = uris.map { uri ->
                DraftStorageManager.takePersistablePermissionIfPossible(appContext, uri)
                metadataReader.readMediaClip(uri)
            }
            updateProjectState(syncPreview = true) { state ->
                val merged = state.clips + newClips
                val activeId = state.selectedClipId ?: newClips.firstOrNull()?.id
                state.copy(clips = merged, selectedClipId = activeId)
            }
        }
    }

    fun removeClip(clipId: String) {
        pausePlayback()
        updateProjectState(syncPreview = true, seekAfterSyncMs = 0L) { state ->
            val remaining = state.clips.filterNot { it.id == clipId }
            val nextSelected = if (state.selectedClipId == clipId) {
                remaining.firstOrNull()?.id
            } else {
                state.selectedClipId
            }
            state.copy(
                clips = remaining,
                selectedClipId = nextSelected,
                currentPositionMs = 0L
            )
        }
    }

    fun moveClipUp(index: Int) {
        if (index <= 0) return
        pausePlayback()
        updateProjectState(syncPreview = true) { state ->
            val mutable = state.clips.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index - 1, item)
            state.copy(clips = mutable)
        }
    }

    fun moveClipDown(index: Int) {
        val currentClips = _uiState.value.clips
        if (index < 0 || index >= currentClips.lastIndex) return
        pausePlayback()
        updateProjectState(syncPreview = true) { state ->
            val mutable = state.clips.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index + 1, item)
            state.copy(clips = mutable)
        }
    }

    fun updateClipTrim(clipId: String, startMs: Long, endMs: Long) {
        pausePlayback()
        updateProjectState(
            continuousKey = "trim_$clipId",
            syncPreview = true,
            seekAfterSyncMs = 0L
        ) { state ->
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) {
                    val safeStart = startMs.coerceIn(0L, clip.originalDurationMs)
                    val safeEnd = endMs.coerceIn(safeStart + 200L, clip.originalDurationMs)
                    clip.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
                } else {
                    clip
                }
            }
            state.copy(clips = updatedClips, currentPositionMs = 0L)
        }
    }

    fun updateImageDuration(clipId: String, durationMs: Long) {
        pausePlayback()
        updateProjectState(
            continuousKey = "img_dur_$clipId",
            syncPreview = true
        ) { state ->
            val safeDur = durationMs.coerceIn(1000L, 60000L)
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId && clip.isImage) {
                    clip.copy(
                        originalDurationMs = safeDur,
                        trimStartMs = 0L,
                        trimEndMs = safeDur
                    )
                } else {
                    clip
                }
            }
            state.copy(clips = updatedClips)
        }
    }

    fun setClipLoopCount(clipId: String, loopCount: Int) {
        pausePlayback()
        updateProjectState(syncPreview = true) { state ->
            val safeLoop = loopCount.coerceIn(1, 20)
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) clip.copy(loopCount = safeLoop) else clip
            }
            val nextTransition = if (safeLoop > 1 && state.transitionConfig.transitionType == TransitionType.NONE) {
                state.transitionConfig.copy(transitionType = TransitionType.CROSSFADE)
            } else {
                state.transitionConfig
            }
            state.copy(clips = updatedClips, transitionConfig = nextTransition)
        }
    }

    fun setClipMotionEffect(clipId: String, effect: CameraMotionEffect) {
        updateProjectState(syncPreview = true) { state ->
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) clip.copy(motionEffect = effect) else clip
            }
            state.copy(clips = updatedClips)
        }
    }

    fun setClipMotionIntensity(clipId: String, intensity: Float) {
        updateProjectState(
            continuousKey = "motion_int_$clipId",
            syncPreview = true
        ) { state ->
            val safeVal = intensity.coerceIn(0.05f, 0.50f)
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) clip.copy(motionIntensity = safeVal) else clip
            }
            state.copy(clips = updatedClips)
        }
    }

    fun applyMotionEffectToAllClips(effect: CameraMotionEffect, intensity: Float) {
        updateProjectState(syncPreview = true) { state ->
            val safeVal = intensity.coerceIn(0.05f, 0.50f)
            val updatedClips = state.clips.map {
                it.copy(motionEffect = effect, motionIntensity = safeVal)
            }
            state.copy(clips = updatedClips)
        }
    }

    fun setClipSpeed(clipId: String, speed: Float) {
        pausePlayback()
        updateProjectState(
            continuousKey = "speed_$clipId",
            syncPreview = true
        ) { state ->
            val safeSpeed = speed.coerceIn(0.25f, 3.0f)
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId && !clip.isImage) clip.copy(speed = safeSpeed) else clip
            }
            state.copy(clips = updatedClips)
        }
    }

    fun applySpeedToAllClips(speed: Float) {
        pausePlayback()
        updateProjectState(syncPreview = true) { state ->
            val safeSpeed = speed.coerceIn(0.25f, 3.0f)
            val updatedClips = state.clips.map {
                if (!it.isImage) it.copy(speed = safeSpeed) else it
            }
            state.copy(clips = updatedClips)
        }
    }

    fun rotateClip90(clipId: String) {
        updateProjectState(syncPreview = true) { state ->
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
        updateProjectState(syncPreview = true) { state ->
            val updatedClips = state.clips.map { clip ->
                if (clip.id == clipId) clip.copy(isMirrored = !clip.isMirrored) else clip
            }
            state.copy(clips = updatedClips)
        }
    }

    fun cutClipAtTimelinePosition(clipId: String, timelineTargetMs: Long) {
        pausePlayback()
        updateProjectState(syncPreview = true) { state ->
            val targetIndex = state.clips.indexOfFirst { it.id == clipId }
            if (targetIndex == -1) return@updateProjectState state

            var startInTimeline = 0L
            for (i in 0 until targetIndex) {
                startInTimeline += state.clips[i].effectiveDurationMs
            }

            val targetClip = state.clips[targetIndex]
            val singlePlayedDur = targetClip.singlePlayDurationMs
            if (singlePlayedDur < 500L) return@updateProjectState state

            val localPlayedOffset = timelineTargetMs - startInTimeline
            val safePlayedOffset = if (localPlayedOffset in 250L..(singlePlayedDur - 250L)) {
                localPlayedOffset
            } else {
                singlePlayedDur / 2L
            }

            val baseName = targetClip.displayName.substringBeforeLast('.')
            val partA: VideoClip
            val partB: VideoClip

            if (targetClip.isImage) {
                val durA = safePlayedOffset.coerceAtLeast(300L)
                val durB = (singlePlayedDur - durA).coerceAtLeast(300L)
                partA = targetClip.copy(
                    id = UUID.randomUUID().toString(),
                    displayName = "$baseName (1)",
                    originalDurationMs = durA,
                    trimStartMs = 0L,
                    trimEndMs = durA,
                    loopCount = 1
                )
                partB = targetClip.copy(
                    id = UUID.randomUUID().toString(),
                    displayName = "$baseName (2)",
                    originalDurationMs = durB,
                    trimStartMs = 0L,
                    trimEndMs = durB,
                    loopCount = 1
                )
            } else {
                val rawOffset = (safePlayedOffset * targetClip.speed).toLong()
                val splitPointMs = (targetClip.trimStartMs + rawOffset)
                    .coerceIn(targetClip.trimStartMs + 200L, targetClip.trimEndMs - 200L)

                partA = targetClip.copy(
                    id = UUID.randomUUID().toString(),
                    displayName = "$baseName (A)",
                    trimEndMs = splitPointMs,
                    loopCount = 1
                )
                partB = targetClip.copy(
                    id = UUID.randomUUID().toString(),
                    displayName = "$baseName (B)",
                    trimStartMs = splitPointMs,
                    loopCount = 1
                )
            }

            val mutable = state.clips.toMutableList()
            mutable.removeAt(targetIndex)
            mutable.add(targetIndex, partB)
            mutable.add(targetIndex, partA)

            state.copy(clips = mutable, selectedClipId = partA.id)
        }
    }

    fun setJoinMode(mode: JoinMode) {
        pausePlayback()
        updateProjectState(syncPreview = true) { state ->
            state.copy(
                transitionConfig = state.transitionConfig.copy(joinMode = mode)
            )
        }
    }

    fun setTransitionType(type: TransitionType) {
        updateProjectState(syncPreview = false) { state ->
            state.copy(
                transitionConfig = state.transitionConfig.copy(transitionType = type)
            )
        }
    }

    fun setTransitionDuration(durationMs: Long) {
        pausePlayback()
        updateProjectState(
            continuousKey = "trans_dur",
            syncPreview = true
        ) { state ->
            state.copy(
                transitionConfig = state.transitionConfig.copy(durationMs = durationMs)
            )
        }
    }

    fun setVideoEndBehavior(behavior: VideoEndBehavior) {
        pausePlayback()
        updateProjectState(syncPreview = true) { state ->
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
            state.copy(transitionConfig = nextTransition)
        }
    }

    // --- Penapis Warna & Pencahayaan ---
    fun setColorPreset(preset: ColorFilterPreset) {
        updateProjectState(syncPreview = false) { state ->
            state.copy(colorConfig = state.colorConfig.copy(preset = preset))
        }
    }

    fun setBrightness(value: Float) {
        updateProjectState(continuousKey = "brightness", syncPreview = false) { state ->
            state.copy(colorConfig = state.colorConfig.copy(brightness = value.coerceIn(-0.4f, 0.4f)))
        }
    }

    fun setContrast(value: Float) {
        updateProjectState(continuousKey = "contrast", syncPreview = false) { state ->
            state.copy(colorConfig = state.colorConfig.copy(contrast = value.coerceIn(0.6f, 1.5f)))
        }
    }

    fun setSaturation(value: Float) {
        updateProjectState(continuousKey = "saturation", syncPreview = false) { state ->
            state.copy(colorConfig = state.colorConfig.copy(saturation = value.coerceIn(0f, 1.8f)))
        }
    }

    fun resetColorConfig() {
        updateProjectState(syncPreview = false) { state ->
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

    // --- Pengurusan Audio & Auto-Beat ---
    fun selectAudioTrack(uri: Uri, extractedFromVideo: Boolean = false) {
        pausePlayback()
        DraftStorageManager.takePersistablePermissionIfPossible(appContext, uri)
        viewModelScope.launch {
            val audioTrack = metadataReader.readAudioTrack(uri, extractedFromVideo)
            updateProjectState(syncPreview = true, seekAfterSyncMs = 0L) { state ->
                val preservedConfig = audioTrack.copy(
                    isOriginalAudioMuted = state.audioConfig.isOriginalAudioMuted,
                    originalVideoVolume = state.audioConfig.originalVideoVolume,
                    musicVolume = state.audioConfig.musicVolume,
                    fadeInDurationMs = state.audioConfig.fadeInDurationMs,
                    fadeOutDurationMs = state.audioConfig.fadeOutDurationMs
                )
                state.copy(audioConfig = preservedConfig, currentPositionMs = 0L)
            }
        }
    }

    fun removeAudioTrack() {
        pausePlayback()
        updateProjectState(syncPreview = true, seekAfterSyncMs = 0L) { state ->
            state.copy(
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
        }
    }

    fun setAudioStartOffset(offsetMs: Long) {
        pausePlayback()
        updateProjectState(
            continuousKey = "audio_offset",
            syncPreview = true,
            seekAfterSyncMs = 0L
        ) { state ->
            val maxOffset = (state.audioConfig.totalDurationMs - 500L).coerceAtLeast(0L)
            val safeOffset = offsetMs.coerceIn(0L, maxOffset)
            state.copy(
                audioConfig = state.audioConfig.copy(startOffsetMs = safeOffset),
                currentPositionMs = 0L
            )
        }
    }

    fun toggleMuteOriginalAudio() {
        updateProjectState(syncPreview = true) { state ->
            val nextMute = !state.audioConfig.isOriginalAudioMuted
            val nextVol = if (nextMute) 0f else {
                if (state.audioConfig.originalVideoVolume <= 0.01f) 0.5f
                else state.audioConfig.originalVideoVolume
            }
            state.copy(
                audioConfig = state.audioConfig.copy(
                    isOriginalAudioMuted = nextMute,
                    originalVideoVolume = nextVol
                )
            )
        }
    }

    fun setOriginalVideoVolume(volume: Float) {
        updateProjectState(continuousKey = "orig_vol", syncPreview = true) { state ->
            val clamped = volume.coerceIn(0f, 1f)
            state.copy(
                audioConfig = state.audioConfig.copy(
                    isOriginalAudioMuted = clamped <= 0.01f,
                    originalVideoVolume = clamped
                )
            )
        }
    }

    fun setMusicVolume(volume: Float) {
        updateProjectState(continuousKey = "music_vol", syncPreview = true) { state ->
            state.copy(
                audioConfig = state.audioConfig.copy(musicVolume = volume.coerceIn(0f, 1f))
            )
        }
    }

    fun setFadeInDuration(fadeInMs: Long) {
        updateProjectState(syncPreview = true) { state ->
            state.copy(
                audioConfig = state.audioConfig.copy(fadeInDurationMs = fadeInMs)
            )
        }
    }

    fun setFadeOutDuration(fadeOutMs: Long) {
        updateProjectState(syncPreview = true) { state ->
            state.copy(
                audioConfig = state.audioConfig.copy(fadeOutDurationMs = fadeOutMs)
            )
        }
    }

    fun autoDetectBeats(sensitivity: BeatSensitivity) {
        val currentState = _uiState.value
        val audioUri = currentState.audioConfig.uri ?: return
        if (currentState.isDetectingBeats) return

        pausePlayback()
        viewModelScope.launch {
            _uiState.update { it.copy(isDetectingBeats = true) }
            val detected = BeatDetector.detectBeats(
                context = appContext,
                uri = audioUri,
                startOffsetMs = currentState.audioConfig.startOffsetMs,
                sensitivity = sensitivity
            )
            updateProjectState(syncPreview = false) { state ->
                state.copy(
                    isDetectingBeats = false,
                    audioConfig = state.audioConfig.copy(beatMarkersMs = detected)
                )
            }
        }
    }

    fun autoSyncClipsToBeats(repeatClipsToFillAllBeats: Boolean) {
        pausePlayback()
        updateProjectState(syncPreview = true, seekAfterSyncMs = 0L) { state ->
            val clips = state.clips
            val beats = state.audioConfig.beatMarkersMs.filter { it >= 250L }.sorted()
            if (clips.isEmpty() || beats.isEmpty()) return@updateProjectState state

            val intervalsMs = mutableListOf<Long>()
            var prevBeat = 0L
            for (beat in beats) {
                val dur = (beat - prevBeat).coerceAtLeast(250L)
                intervalsMs.add(dur)
                prevBeat = beat
            }
            val totalAudioDur = state.audioConfig.effectiveAudioDurationMs
            if (totalAudioDur > prevBeat + 300L) {
                intervalsMs.add(totalAudioDur - prevBeat)
            }

            val syncedClips = mutableListOf<VideoClip>()
            val targetCount = if (repeatClipsToFillAllBeats) {
                intervalsMs.size
            } else {
                minOf(clips.size, intervalsMs.size)
            }

            for (i in 0 until targetCount) {
                val templateClip = clips[i % clips.size]
                val slotDurMs = intervalsMs[i]

                val adjustedClip = if (templateClip.isImage) {
                    templateClip.copy(
                        id = UUID.randomUUID().toString(),
                        originalDurationMs = slotDurMs,
                        trimStartMs = 0L,
                        trimEndMs = slotDurMs,
                        loopCount = 1
                    )
                } else {
                    val safeSpeed = templateClip.speed.coerceIn(0.25f, 4.0f)
                    val neededRawMs = (slotDurMs * safeSpeed).toLong()
                    val availableRawMs = (templateClip.originalDurationMs - templateClip.trimStartMs)
                        .coerceAtLeast(250L)
                    val finalRawDur = minOf(neededRawMs, availableRawMs)
                    templateClip.copy(
                        id = UUID.randomUUID().toString(),
                        trimEndMs = templateClip.trimStartMs + finalRawDur,
                        loopCount = 1
                    )
                }
                syncedClips.add(adjustedClip)
            }

            if (!repeatClipsToFillAllBeats && clips.size > targetCount) {
                syncedClips.addAll(clips.subList(targetCount, clips.size))
            }

            state.copy(
                clips = syncedClips,
                selectedClipId = syncedClips.firstOrNull()?.id,
                currentPositionMs = 0L
            )
        }
    }

    fun addBeatMarkerAtCurrentPosition() {
        val pos = _uiState.value.currentPositionMs
        val existing = _uiState.value.audioConfig.beatMarkersMs
        if (existing.any { kotlin.math.abs(it - pos) < 120L }) return

        updateProjectState(syncPreview = false) { state ->
            val updatedBeats = (state.audioConfig.beatMarkersMs + pos).sorted()
            state.copy(audioConfig = state.audioConfig.copy(beatMarkersMs = updatedBeats))
        }
    }

    fun removeBeatMarker(markerMs: Long) {
        updateProjectState(syncPreview = false) { state ->
            val updatedBeats = state.audioConfig.beatMarkersMs.filterNot { it == markerMs }
            state.copy(audioConfig = state.audioConfig.copy(beatMarkersMs = updatedBeats))
        }
    }

    fun clearBeatMarkers() {
        updateProjectState(syncPreview = false) { state ->
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
        updateProjectState(syncPreview = false) { it.copy(aspectRatio = preset) }
    }

    fun setCropScaleMode(mode: CropScaleMode) {
        updateProjectState(syncPreview = false) { it.copy(cropScaleMode = mode) }
    }

    // --- Sarikata & Import Fail Lirik (.LRC / .SRT) ---
    fun upsertSubtitle(item: SubtitleItem) {
        updateProjectState(syncPreview = false) { state ->
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
        updateProjectState(syncPreview = false) { state ->
            state.copy(subtitles = state.subtitles.filterNot { it.id == subtitleId })
        }
    }

    fun clearAllSubtitles() {
        updateProjectState(syncPreview = false) { it.copy(subtitles = emptyList()) }
    }

    fun importSubtitlesFromUri(uri: Uri) {
        viewModelScope.launch {
            val maxDur = _uiState.value.finalTimelineDurationMs.coerceAtLeast(180_000L)
            val imported = SubtitleParser.parseFromUri(appContext, uri, maxDur)
            if (imported.isNotEmpty()) {
                updateProjectState(syncPreview = false) { state ->
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
        updateProjectState(syncPreview = false) { state ->
            state.copy(
                watermarkConfig = state.watermarkConfig.copy(
                    uri = uri,
                    displayName = name
                )
            )
        }
    }

    fun removeWatermark() {
        updateProjectState(syncPreview = false) { state ->
            state.copy(
                watermarkConfig = state.watermarkConfig.copy(
                    uri = null,
                    displayName = ""
                )
            )
        }
    }

    fun setWatermarkScale(scale: Float) {
        updateProjectState(continuousKey = "wm_scale", syncPreview = false) { state ->
            state.copy(
                watermarkConfig = state.watermarkConfig.copy(
                    scalePercent = scale.coerceIn(0.08f, 0.55f)
                )
            )
        }
    }

    fun setWatermarkOpacity(opacity: Float) {
        updateProjectState(continuousKey = "wm_opacity", syncPreview = false) { state ->
            state.copy(
                watermarkConfig = state.watermarkConfig.copy(
                    opacity = opacity.coerceIn(0.15f, 1.0f)
                )
            )
        }
    }

    fun setWatermarkPosition(position: WatermarkPosition) {
        updateProjectState(syncPreview = false) { state ->
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
                undoStack.clear()
                redoStack.clear()
                lastContinuousActionKey = ""
                val cleanLoaded = loadedState.copy(
                    canUndo = false,
                    canRedo = false,
                    currentPositionMs = 0L
                )
                _uiState.value = cleanLoaded
                syncPreviewWithState(cleanLoaded)
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
