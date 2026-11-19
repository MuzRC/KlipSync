package com.muzrc.klipsync.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.muzrc.klipsync.ui.dialogs.ExportDialog
import com.muzrc.klipsync.ui.dialogs.SubtitleEditorDialog
import com.muzrc.klipsync.ui.dialogs.UpdateDialog
import com.muzrc.klipsync.ui.tabs.AudioTabPanel
import com.muzrc.klipsync.ui.tabs.ClipsTabPanel
import com.muzrc.klipsync.ui.tabs.CropTabPanel
import com.muzrc.klipsync.ui.tabs.SubtitlesTabPanel
import com.muzrc.klipsync.viewmodel.EditorViewModel

@OptIn(UnstableApi::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val state by viewModel.uiState.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var editingSubtitle by remember { mutableStateOf<SubtitleItem?>(null) }

    val isInFreezeFrameZone = state.clips.isNotEmpty() &&
        state.freezeFrameExtensionMs > 0L &&
        state.currentPositionMs >= state.totalVideoDurationMs

    Scaffold(
        topBar = {
            EditorTopBar(
                hasClips = state.clips.isNotEmpty(),
                isCheckingUpdate = state.updateCheckState is UpdateCheckState.Checking,
                onCheckUpdateClick = { viewModel.checkForUpdatesManual() },
                onExportClick = {
                    viewModel.pausePlayback()
                    showExportDialog = true
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            LivePreviewCanvas(
                videoPlayer = viewModel.playerController.videoPlayer,
                hasClips = state.clips.isNotEmpty(),
                aspectRatio = state.aspectRatio,
                subtitles = state.subtitles,
                currentPositionMs = state.currentPositionMs,
                totalVideoDurationMs = state.totalVideoDurationMs,
                freezeFrameExtensionMs = state.freezeFrameExtensionMs,
                isInFreezeFrameZone = isInFreezeFrameZone,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.36f)
            )

            PlaybackControlBar(
                isPlaying = state.isPlaying,
                currentPositionMs = state.currentPositionMs,
                totalDurationMs = state.finalTimelineDurationMs,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSeek = { viewModel.seekTo(it) }
            )

            HorizontalDivider()

            MultiTrackTimeline(
                clips = state.clips,
                audioConfig = state.audioConfig,
                subtitles = state.subtitles,
                freezeFrameExtensionMs = state.freezeFrameExtensionMs,
                autoLoopExtensionMs = state.autoLoopExtensionMs
            )

            HorizontalDivider()

            EditorTabBar(
                activeTab = state.activeTab,
                onSelectTab = { viewModel.selectTab(it) }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.64f)
            ) {
                when (state.activeTab) {
                    EditorTab.CLIPS -> ClipsTabPanel(
                        clips = state.clips,
                        transitionConfig = state.transitionConfig,
                        hasLongerAudio = state.extraAudioExtensionMs > 0L,
                        onAddClips = { viewModel.addVideoClips(it) },
                        onRemoveClip = { viewModel.removeClip(it) },
                        onMoveUp = { viewModel.moveClipUp(it) },
                        onMoveDown = { viewModel.moveClipDown(it) },
                        onTrimClip = { id, s, e -> viewModel.updateClipTrim(id, s, e) },
                        onCutClip = { viewModel.cutClipAtCurrentPosition(it) },
                        onLoopCountChange = { id, count -> viewModel.setClipLoopCount(id, count) },
                        onJoinModeChange = { viewModel.setJoinMode(it) },
                        onTransitionTypeChange = { viewModel.setTransitionType(it) },
                        onTransitionDurationChange = { viewModel.setTransitionDuration(it) },
                        onEndBehaviorChange = { viewModel.setVideoEndBehavior(it) }
                    )

                    EditorTab.AUDIO -> AudioTabPanel(
                        audioConfig = state.audioConfig,
                        onSelectAudio = { viewModel.selectAudioTrack(it) },
                        onRemoveAudio = { viewModel.removeAudioTrack() },
                        onStartOffsetChange = { viewModel.setAudioStartOffset(it) },
                        onToggleMuteOriginal = { viewModel.toggleMuteOriginalAudio() },
                        onOriginalVolumeChange = { viewModel.setOriginalVideoVolume(it) },
                        onMusicVolumeChange = { viewModel.setMusicVolume(it) },
                        onFadeInChange = { viewModel.setFadeInDuration(it) },
                        onFadeOutChange = { viewModel.setFadeOutDuration(it) }
                    )

                    EditorTab.CROP -> CropTabPanel(
                        selectedRatio = state.aspectRatio,
                        onSelectRatio = { viewModel.setAspectRatio(it) }
                    )

                    EditorTab.SUBTITLES -> SubtitlesTabPanel(
                        subtitles = state.subtitles,
                        onAddNewClick = {
                            viewModel.pausePlayback()
                            editingSubtitle = null
                            showSubtitleDialog = true
                        },
                        onEditClick = { item ->
                            viewModel.pausePlayback()
                            editingSubtitle = item
                            showSubtitleDialog = true
                        },
                        onDeleteClick = { viewModel.removeSubtitle(it) }
                    )
                }
            }
        }
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
        onDismiss = { viewModel.dismissUpdateDialog() }
    )
}
