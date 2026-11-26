package com.muzrc.klipsync.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.muzrc.klipsync.model.EditorTab
import com.muzrc.klipsync.model.ExportState
import com.muzrc.klipsync.model.SubtitleItem
import com.muzrc.klipsync.model.UpdateCheckState
import com.muzrc.klipsync.ui.components.EditorTabBar
import com.muzrc.klipsync.ui.components.EditorTopBar
import com.muzrc.klipsync.ui.components.LivePreviewCanvas
import com.muzrc.klipsync.ui.components.MultiTrackTimeline
import com.muzrc.klipsync.ui.components.PlaybackControlBar
import com.muzrc.klipsync.ui.dialogs.DraftsDialog
import com.muzrc.klipsync.ui.dialogs.ExportDialog
import com.muzrc.klipsync.ui.dialogs.SubtitleEditorDialog
import com.muzrc.klipsync.ui.dialogs.UpdateDialog
import com.muzrc.klipsync.ui.tabs.AudioTabPanel
import com.muzrc.klipsync.ui.tabs.BeatTabPanel
import com.muzrc.klipsync.ui.tabs.ClipsTabPanel
import com.muzrc.klipsync.ui.tabs.ColorTabPanel
import com.muzrc.klipsync.ui.tabs.CropTabPanel
import com.muzrc.klipsync.ui.tabs.DevLogTabPanel
import com.muzrc.klipsync.ui.tabs.SpeedTabPanel
import com.muzrc.klipsync.ui.tabs.SubtitlesTabPanel
import com.muzrc.klipsync.ui.tabs.TemplatesTabPanel
import com.muzrc.klipsync.ui.tabs.WatermarkTabPanel
import com.muzrc.klipsync.util.ExportDebugLogger
import com.muzrc.klipsync.util.TimeFormatter
import com.muzrc.klipsync.viewmodel.EditorViewModel

@OptIn(UnstableApi::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val state by viewModel.uiState.collectAsState()
    val drafts by viewModel.drafts.collectAsState()
    val exportLogs by ExportDebugLogger.logsFlow.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var showDraftsDialog by remember { mutableStateOf(false) }
    var showNewProjectConfirmDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var editingSubtitle by remember { mutableStateOf<SubtitleItem?>(null) }

    var isPreviewVisible by remember { mutableStateOf(state.activeTab.isVisualContextTab()) }
    var isFullscreenPreview by remember { mutableStateOf(false) }
    var showFullscreenControls by remember { mutableStateOf(true) }

    LaunchedEffect(state.activeTab) {
        isPreviewVisible = state.activeTab.isVisualContextTab()
    }

    BackHandler(enabled = isFullscreenPreview) {
        isFullscreenPreview = false
    }

    val isInFreezeFrameZone = state.clips.isNotEmpty() &&
        state.freezeFrameExtensionMs > 0L &&
        state.currentPositionMs >= state.totalVideoDurationMs

    val currentPlayingClip = viewModel.playerController.currentActiveClipAt(state.currentPositionMs)
        ?: state.selectedClip
    val transitionVisual = viewModel.playerController.computePreviewTransitionVisual(
        positionMs = state.currentPositionMs,
        transitionType = state.transitionConfig.transitionType,
        transitionDurationMs = state.transitionConfig.durationMs
    )
    val motionVisual = viewModel.playerController.computeClipMotionVisual(
        positionMs = state.currentPositionMs
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isFullscreenPreview) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    EditorTopBar(
                        hasClips = state.clips.isNotEmpty(),
                        canUndo = state.canUndo,
                        canRedo = state.canRedo,
                        onNewProjectClick = {
                            viewModel.pausePlayback()
                            if (state.hasAnyContent) {
                                showNewProjectConfirmDialog = true
                            } else {
                                viewModel.createNewProject()
                            }
                        },
                        onUndoClick = { viewModel.undo() },
                        onRedoClick = { viewModel.redo() },
                        onDraftsClick = {
                            viewModel.pausePlayback()
                            viewModel.refreshDrafts()
                            showDraftsDialog = true
                        },
                        onExportClick = {
                            viewModel.pausePlayback()
                            showExportDialog = true
                        }
                    )
                }
            ) { innerPadding ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val previewHeight = (maxHeight * 0.31f).coerceIn(170.dp, 260.dp)

                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = isPreviewVisible,
                            enter = expandVertically(
                                expandFrom = Alignment.Top,
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                            exit = shrinkVertically(
                                shrinkTowards = Alignment.Top,
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(durationMillis = 150))
                        ) {
                            LivePreviewCanvas(
                                videoPlayer = viewModel.playerController.videoPlayer,
                                hasClips = state.clips.isNotEmpty(),
                                activeClip = currentPlayingClip,
                                aspectRatio = state.aspectRatio,
                                cropScaleMode = state.cropScaleMode,
                                colorConfig = state.colorConfig,
                                subtitles = state.subtitles,
                                watermarkConfig = state.watermarkConfig,
                                currentPositionMs = state.currentPositionMs,
                                totalVideoDurationMs = state.totalVideoDurationMs,
                                freezeFrameExtensionMs = state.freezeFrameExtensionMs,
                                isInFreezeFrameZone = isInFreezeFrameZone,
                                transitionVisual = transitionVisual,
                                motionVisual = motionVisual,
                                isFullscreen = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(previewHeight)
                            )
                        }

                        PlaybackControlBar(
                            isPlaying = state.isPlaying,
                            currentPositionMs = state.currentPositionMs,
                            totalDurationMs = state.finalTimelineDurationMs,
                            isPreviewVisible = isPreviewVisible,
                            onTogglePreview = { isPreviewVisible = !isPreviewVisible },
                            onEnterFullscreen = {
                                isPreviewVisible = true
                                showFullscreenControls = true
                                isFullscreenPreview = true
                            },
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onSeek = { viewModel.seekTo(it) }
                        )

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        MultiTrackTimeline(
                            clips = state.clips,
                            selectedClipId = state.selectedClip?.id,
                            audioConfig = state.audioConfig,
                            subtitles = state.subtitles,
                            freezeFrameExtensionMs = state.freezeFrameExtensionMs,
                            autoLoopExtensionMs = state.autoLoopExtensionMs,
                            currentPositionMs = state.currentPositionMs,
                            onSelectClip = {
                                viewModel.selectClip(it)
                                viewModel.selectTab(EditorTab.CLIPS)
                            },
                            onOpenClipsTab = { viewModel.selectTab(EditorTab.CLIPS) },
                            onOpenAudioTab = { viewModel.selectTab(EditorTab.AUDIO) },
                            onOpenSubtitlesTab = { viewModel.selectTab(EditorTab.SUBTITLES) }
                        )

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            when (state.activeTab) {
                                EditorTab.CLIPS -> ClipsTabPanel(
                                    clips = state.clips,
                                    selectedClipId = state.selectedClip?.id,
                                    transitionConfig = state.transitionConfig,
                                    hasLongerAudio = state.extraAudioExtensionMs > 0L,
                                    onSelectClip = { viewModel.selectClip(it) },
                                    onAddClips = { viewModel.addVideoClips(it) },
                                    onRemoveClip = { viewModel.removeClip(it) },
                                    onMoveUp = { viewModel.moveClipUp(it) },
                                    onMoveDown = { viewModel.moveClipDown(it) },
                                    onTrimClip = { id, s, e -> viewModel.updateClipTrim(id, s, e) },
                                    onImageDurationChange = { id, dur -> viewModel.updateImageDuration(id, dur) },
                                    onCutClip = { id -> viewModel.cutClipAtTimelinePosition(id, state.currentPositionMs) },
                                    onLoopCountChange = { id, count -> viewModel.setClipLoopCount(id, count) },
                                    onRotateClip = { viewModel.rotateClip90(it) },
                                    onMirrorClip = { viewModel.toggleClipMirror(it) },
                                    onMotionEffectChange = { id, eff -> viewModel.setClipMotionEffect(id, eff) },
                                    onMotionIntensityChange = { id, amt -> viewModel.setClipMotionIntensity(id, amt) },
                                    onApplyMotionToAll = { eff, amt -> viewModel.applyMotionEffectToAllClips(eff, amt) },
                                    onJoinModeChange = { viewModel.setJoinMode(it) },
                                    onTransitionTypeChange = { viewModel.setTransitionType(it) },
                                    onTransitionDurationChange = { viewModel.setTransitionDuration(it) },
                                    onEndBehaviorChange = { viewModel.setVideoEndBehavior(it) }
                                )

                                EditorTab.TEMPLATES -> TemplatesTabPanel(
                                    activeTemplateId = state.activeTemplateId,
                                    hasClips = state.clips.isNotEmpty(),
                                    onApplyTemplate = { template -> viewModel.applyTemplate(template) },
                                    onResetTemplate = { viewModel.resetTemplateToDefault() }
                                )

                                EditorTab.SPEED -> SpeedTabPanel(
                                    selectedClip = state.selectedClip,
                                    hasMultipleClips = state.clips.size > 1,
                                    onSpeedChange = { id, spd -> viewModel.setClipSpeed(id, spd) },
                                    onApplyToAll = { spd -> viewModel.applySpeedToAllClips(spd) }
                                )

                                EditorTab.COLOR -> ColorTabPanel(
                                    colorConfig = state.colorConfig,
                                    onPresetChange = { viewModel.setColorPreset(it) },
                                    onBrightnessChange = { viewModel.setBrightness(it) },
                                    onContrastChange = { viewModel.setContrast(it) },
                                    onSaturationChange = { viewModel.setSaturation(it) },
                                    onReset = { viewModel.resetColorConfig() }
                                )

                                EditorTab.AUDIO -> AudioTabPanel(
                                    audioConfig = state.audioConfig,
                                    onSelectAudio = { uri, extracted -> viewModel.selectAudioTrack(uri, extracted) },
                                    onRemoveAudio = { viewModel.removeAudioTrack() },
                                    onStartOffsetChange = { viewModel.setAudioStartOffset(it) },
                                    onToggleMuteOriginal = { viewModel.toggleMuteOriginalAudio() },
                                    onOriginalVolumeChange = { viewModel.setOriginalVideoVolume(it) },
                                    onMusicVolumeChange = { viewModel.setMusicVolume(it) },
                                    onFadeInChange = { viewModel.setFadeInDuration(it) },
                                    onFadeOutChange = { viewModel.setFadeOutDuration(it) }
                                )

                                EditorTab.BEAT -> BeatTabPanel(
                                    currentPositionMs = state.currentPositionMs,
                                    beatMarkersMs = state.audioConfig.beatMarkersMs,
                                    hasClips = state.clips.isNotEmpty(),
                                    hasAudio = state.audioConfig.uri != null,
                                    isDetectingBeats = state.isDetectingBeats,
                                    onAutoDetectBeats = { viewModel.autoDetectBeats(it) },
                                    onAutoSyncClipsToBeats = { repeat -> viewModel.autoSyncClipsToBeats(repeat) },
                                    onTapBeat = { viewModel.addBeatMarkerAtCurrentPosition() },
                                    onSeekToBeat = { viewModel.seekTo(it) },
                                    onCutAtBeat = { viewModel.cutClipAtBeat(it) },
                                    onRemoveBeat = { viewModel.removeBeatMarker(it) },
                                    onClearBeats = { viewModel.clearBeatMarkers() }
                                )

                                EditorTab.CROP -> CropTabPanel(
                                    selectedRatio = state.aspectRatio,
                                    selectedScaleMode = state.cropScaleMode,
                                    onSelectRatio = { viewModel.setAspectRatio(it) },
                                    onSelectScaleMode = { viewModel.setCropScaleMode(it) }
                                )

                                EditorTab.SUBTITLES -> SubtitlesTabPanel(
                                    subtitles = state.subtitles,
                                    onAddNewClick = {
                                        viewModel.pausePlayback()
                                        editingSubtitle = null
                                        showSubtitleDialog = true
                                    },
                                    onImportLrcClick = { viewModel.importSubtitlesFromUri(it) },
                                    onEditClick = { item ->
                                        viewModel.pausePlayback()
                                        editingSubtitle = item
                                        showSubtitleDialog = true
                                    },
                                    onDeleteClick = { viewModel.removeSubtitle(it) },
                                    onClearAllClick = { viewModel.clearAllSubtitles() }
                                )

                                EditorTab.WATERMARK -> WatermarkTabPanel(
                                    config = state.watermarkConfig,
                                    onSelectLogo = { viewModel.selectWatermarkImage(it) },
                                    onRemoveLogo = { viewModel.removeWatermark() },
                                    onScaleChange = { viewModel.setWatermarkScale(it) },
                                    onOpacityChange = { viewModel.setWatermarkOpacity(it) },
                                    onPositionChange = { viewModel.setWatermarkPosition(it) }
                                )

                                EditorTab.DEV_LOG -> DevLogTabPanel(
                                    logText = exportLogs,
                                    isCheckingUpdate = state.updateCheckState is UpdateCheckState.Checking,
                                    onCheckUpdateClick = { viewModel.checkForUpdatesManual() },
                                    onClearLog = { viewModel.clearExportLogs() }
                                )
                            }
                        }

                        EditorTabBar(
                            activeTab = state.activeTab,
                            onSelectTab = { viewModel.selectTab(it) }
                        )
                    }
                }
            }
        } else {
            val tapInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = tapInteraction,
                        indication = null
                    ) {
                        showFullscreenControls = !showFullscreenControls
                    }
            ) {
                LivePreviewCanvas(
                    videoPlayer = viewModel.playerController.videoPlayer,
                    hasClips = state.clips.isNotEmpty(),
                    activeClip = currentPlayingClip,
                    aspectRatio = state.aspectRatio,
                    cropScaleMode = state.cropScaleMode,
                    colorConfig = state.colorConfig,
                    subtitles = state.subtitles,
                    watermarkConfig = state.watermarkConfig,
                    currentPositionMs = state.currentPositionMs,
                    totalVideoDurationMs = state.totalVideoDurationMs,
                    freezeFrameExtensionMs = state.freezeFrameExtensionMs,
                    isInFreezeFrameZone = isInFreezeFrameZone,
                    transitionVisual = transitionVisual,
                    motionVisual = motionVisual,
                    isFullscreen = true,
                    modifier = Modifier.fillMaxSize()
                )

                AnimatedVisibility(
                    visible = showFullscreenControls,
                    enter = fadeIn(animationSpec = tween(180)),
                    exit = fadeOut(animationSpec = tween(180)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.58f))
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { isFullscreenPreview = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FullscreenExit,
                                contentDescription = "Keluar Skrin Penuh",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Keluar Skrin Penuh",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = state.aspectRatio.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showFullscreenControls,
                    enter = fadeIn(animationSpec = tween(180)),
                    exit = fadeOut(animationSpec = tween(180)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val playEnabled = state.finalTimelineDurationMs > 0L
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (playEnabled) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (playEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    CircleShape
                                )
                                .clickable(enabled = playEnabled) { viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Jeda" else "Main",
                                tint = if (playEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = TimeFormatter.formatDurationMs(state.currentPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )

                        val maxVal = state.finalTimelineDurationMs.coerceAtLeast(1L).toFloat()
                        Slider(
                            value = state.currentPositionMs.coerceIn(0L, state.finalTimelineDurationMs).toFloat(),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..maxVal,
                            enabled = playEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.30f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                        )

                        Text(
                            text = TimeFormatter.formatDurationMs(state.finalTimelineDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.80f)
                        )
                    }
                }
            }
        }
    }

    if (showNewProjectConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showNewProjectConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Buat Projek Baharu?") },
            text = {
                Text("Semua klip, lagu dan suntingan yang belum disimpan dalam draf akan dikosongkan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewProject()
                        showNewProjectConfirmDialog = false
                    }
                ) {
                    Text("Ya, Projek Baharu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showDraftsDialog) {
        DraftsDialog(
            currentProjectName = state.projectName,
            hasClips = state.clips.isNotEmpty(),
            drafts = drafts,
            onNewProject = { viewModel.createNewProject() },
            onSaveDraft = { viewModel.saveCurrentDraft(it) },
            onLoadDraft = { viewModel.loadDraft(it) },
            onDeleteDraft = { viewModel.deleteDraft(it) },
            onDismiss = { showDraftsDialog = false }
        )
    }

    if (showSubtitleDialog) {
        SubtitleEditorDialog(
            initialItem = editingSubtitle,
            defaultStartMs = state.currentPositionMs,
            maxTimelineMs = state.finalTimelineDurationMs,
            onDismiss = { showSubtitleDialog = false },
            onSave = { savedItem ->
                viewModel.upsertSubtitle(savedItem)
                showSubtitleDialog = false
            }
        )
    }

    if (showExportDialog || state.exportState !is ExportState.Idle) {
        ExportDialog(
            exportConfig = state.exportConfig,
            exportState = state.exportState,
            finalDurationMs = state.finalTimelineDurationMs,
            onResolutionChange = { viewModel.setExportResolution(it) },
            onFrameRateChange = { viewModel.setExportFrameRate(it) },
            onStartExport = { viewModel.startExport() },
            onDismiss = {
                viewModel.cancelOrResetExport()
                showExportDialog = false
            }
        )
    }

    UpdateDialog(
        state = state.updateCheckState,
        onStartDownload = { release -> viewModel.startUpdateDownload(release) },
        onCancelDownload = { downloadId -> viewModel.cancelUpdateDownload(downloadId) },
        onInstallApk = { downloadId, localUri -> viewModel.installDownloadedUpdate(downloadId, localUri) },
        onDismiss = { viewModel.dismissUpdateDialog() }
    )
}

private fun EditorTab.isVisualContextTab(): Boolean {
    return when (this) {
        EditorTab.CLIPS,
        EditorTab.TEMPLATES,
        EditorTab.SPEED,
        EditorTab.COLOR,
        EditorTab.CROP,
        EditorTab.SUBTITLES,
        EditorTab.WATERMARK -> true
        EditorTab.AUDIO,
        EditorTab.BEAT,
        EditorTab.DEV_LOG -> false
    }
}
