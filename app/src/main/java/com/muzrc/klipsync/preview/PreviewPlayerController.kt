package com.muzrc.klipsync.preview

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.muzrc.klipsync.model.AudioTrackConfig
import com.muzrc.klipsync.model.JoinMode
import com.muzrc.klipsync.model.TransitionConfig
import com.muzrc.klipsync.model.TransitionType
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.model.VideoEndBehavior
import kotlin.math.abs

data class PreviewTransitionVisual(
    val blackOverlayAlpha: Float = 0f,
    val zoomScale: Float = 1f,
    val slideOffsetXFraction: Float = 0f
)

@OptIn(UnstableApi::class)
class PreviewPlayerController(context: Context) {

    val videoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
        setSeekParameters(SeekParameters.CLOSEST_SYNC)
    }

    val audioPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
    }

    private var expandedClipsCache: List<VideoClip> = emptyList()
    private var clipPlayedDurationsMs: List<Long> = emptyList()
    private var totalVideoDurationMs: Long = 0L
    private var audioConfig: AudioTrackConfig = AudioTrackConfig()
    private var endBehavior: VideoEndBehavior = VideoEndBehavior.FREEZE_FRAME
    private var lastVideoSignature: String = ""
    private var lastAudioSignature: String = ""
    private var lastDriftCorrectionRealtimeMs: Long = 0L

    fun syncMedia(
        clips: List<VideoClip>,
        transitionConfig: TransitionConfig,
        audio: AudioTrackConfig
    ) {
        this.audioConfig = audio
        this.endBehavior = transitionConfig.endBehavior

        val videoSignature = buildString {
            append(transitionConfig.joinMode.name).append(":")
            append(transitionConfig.durationMs).append(":")
            append(transitionConfig.endBehavior.name).append("|")
            clips.forEach {
                append("${it.id}:${it.uri}:${it.trimStartMs}:${it.trimEndMs}:${it.loopCount}:${it.speed};")
            }
        }

        if (videoSignature != lastVideoSignature) {
            lastVideoSignature = videoSignature
            rebuildVideoPlaylist(clips, transitionConfig)
        }

        val audioSignature = "${audio.uri}:${audio.startOffsetMs}:${audio.totalDurationMs}"
        if (audioSignature != lastAudioSignature) {
            lastAudioSignature = audioSignature
            rebuildAudioItem(audio)
        }

        applyCurrentClipSpeed()
        applyVolumes(currentTimelinePositionMs())
    }

    private fun rebuildVideoPlaylist(
        clips: List<VideoClip>,
        transitionConfig: TransitionConfig
    ) {
        videoPlayer.stop()
        videoPlayer.clearMediaItems()

        if (clips.isEmpty()) {
            expandedClipsCache = emptyList()
            clipPlayedDurationsMs = emptyList()
            totalVideoDurationMs = 0L
            return
        }

        val expandedClips = clips.flatMap { clip ->
            List(clip.loopCount.coerceAtLeast(1)) { clip }
        }
        expandedClipsCache = expandedClips

        val overlapHalfMs = if (transitionConfig.joinMode == JoinMode.OVERLAP && expandedClips.size > 1) {
            (transitionConfig.durationMs / 2L).coerceAtLeast(0L)
        } else {
            0L
        }

        val playedDurations = mutableListOf<Long>()
        val items = mutableListOf<MediaItem>()

        expandedClips.forEachIndexed { index, clip ->
            val isFirst = index == 0
            val isLast = index == expandedClips.lastIndex
            val safeSpeed = clip.speed.coerceIn(0.25f, 4.0f)

            val start = if (!isFirst) {
                (clip.trimStartMs + (overlapHalfMs * safeSpeed).toLong()).coerceAtMost(clip.trimEndMs)
            } else {
                clip.trimStartMs
            }

            val end = if (!isLast) {
                (clip.trimEndMs - (overlapHalfMs * safeSpeed).toLong()).coerceAtLeast(start)
            } else {
                clip.trimEndMs
            }

            val playedDur = ((end - start) / safeSpeed).toLong().coerceAtLeast(0L)
            playedDurations.add(playedDur)

            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(start)
                .setEndPositionMs(end)
                .build()

            items.add(
                MediaItem.Builder()
                    .setUri(clip.uri)
                    .setClippingConfiguration(clipping)
                    .build()
            )
        }

        clipPlayedDurationsMs = playedDurations
        totalVideoDurationMs = playedDurations.sum()
        videoPlayer.repeatMode = if (transitionConfig.endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
            Player.REPEAT_MODE_ALL
        } else {
            Player.REPEAT_MODE_OFF
        }
        videoPlayer.setMediaItems(items)
        videoPlayer.prepare()
    }

    private fun rebuildAudioItem(audio: AudioTrackConfig) {
        audioPlayer.stop()
        audioPlayer.clearMediaItems()

        val uri = audio.uri ?: return
        if (audio.effectiveAudioDurationMs <= 0L) return

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(audio.startOffsetMs.coerceAtLeast(0L))
            .setEndPositionMs(audio.totalDurationMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clipping)
            .build()

        audioPlayer.setMediaItem(mediaItem)
        audioPlayer.prepare()
    }

    fun currentActiveClip(): VideoClip? {
        if (expandedClipsCache.isEmpty()) return null
        val idx = videoPlayer.currentMediaItemIndex.coerceIn(0, expandedClipsCache.lastIndex)
        return expandedClipsCache[idx]
    }

    /**
     * Mengira kesan visual transisi (fade gelap, zoom, slide) di sempadan klip & loop
     * supaya boleh dirender dengan tepat di atas SurfaceView pratonton.
     */
    fun computePreviewTransitionVisual(
        positionMs: Long,
        transitionType: TransitionType,
        transitionDurationMs: Long
    ): PreviewTransitionVisual {
        if (transitionType == TransitionType.NONE ||
            clipPlayedDurationsMs.isEmpty() ||
            totalVideoDurationMs <= 0L
        ) {
            return PreviewTransitionVisual()
        }

        val hasMultipleSegments = clipPlayedDurationsMs.size > 1 ||
            endBehavior == VideoEndBehavior.LOOP_TO_AUDIO
        if (!hasMultipleSegments) return PreviewTransitionVisual()

        val loopCycleIndex = if (endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
            positionMs / totalVideoDurationMs
        } else {
            0L
        }
        val mappedPos = if (endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
            positionMs % totalVideoDurationMs
        } else {
            positionMs
        }

        var acc = 0L
        for (i in clipPlayedDurationsMs.indices) {
            val dur = clipPlayedDurationsMs[i]
            if (mappedPos < acc + dur || i == clipPlayedDurationsMs.lastIndex) {
                val localPos = (mappedPos - acc).coerceIn(0L, dur)
                val halfTrans = (transitionDurationMs / 2L).coerceAtMost(dur / 2L).coerceAtLeast(1L)

                val isVeryFirstSegment = (loopCycleIndex == 0L && i == 0)
                val isVeryLastSegment = (endBehavior != VideoEndBehavior.LOOP_TO_AUDIO && i == clipPlayedDurationsMs.lastIndex)

                // Fasa Masuk (Awal Segmen)
                if (!isVeryFirstSegment && localPos < halfTrans) {
                    val progress = (localPos.toFloat() / halfTrans.toFloat()).coerceIn(0f, 1f)
                    return when (transitionType) {
                        TransitionType.CROSSFADE -> PreviewTransitionVisual(
                            blackOverlayAlpha = (1f - progress) * 0.75f
                        )
                        TransitionType.FADE_TO_BLACK -> PreviewTransitionVisual(
                            blackOverlayAlpha = 1f - progress
                        )
                        TransitionType.ZOOM_DISSOLVE -> PreviewTransitionVisual(
                            blackOverlayAlpha = (1f - progress) * 0.70f,
                            zoomScale = 1.12f - (0.12f * progress)
                        )
                        TransitionType.SLIDE_LEFT -> PreviewTransitionVisual(
                            slideOffsetXFraction = (1f - progress) * 0.35f
                        )
                        TransitionType.NONE -> PreviewTransitionVisual()
                    }
                }

                // Fasa Keluar (Hujung Segmen)
                if (!isVeryLastSegment && localPos > (dur - halfTrans)) {
                    val remaining = (dur - localPos).coerceAtLeast(0L)
                    val progress = (remaining.toFloat() / halfTrans.toFloat()).coerceIn(0f, 1f)
                    return when (transitionType) {
                        TransitionType.CROSSFADE -> PreviewTransitionVisual(
                            blackOverlayAlpha = (1f - progress) * 0.75f
                        )
                        TransitionType.FADE_TO_BLACK -> PreviewTransitionVisual(
                            blackOverlayAlpha = 1f - progress
                        )
                        TransitionType.ZOOM_DISSOLVE -> PreviewTransitionVisual(
                            blackOverlayAlpha = (1f - progress) * 0.70f,
                            zoomScale = 1.0f + (0.12f * (1f - progress))
                        )
                        TransitionType.SLIDE_LEFT -> PreviewTransitionVisual(
                            slideOffsetXFraction = -(1f - progress) * 0.35f
                        )
                        TransitionType.NONE -> PreviewTransitionVisual()
                    }
                }

                return PreviewTransitionVisual()
            }
            acc += dur
        }
        return PreviewTransitionVisual()
    }

    private fun applyCurrentClipSpeed() {
        val activeClip = currentActiveClip() ?: return
        val targetSpeed = activeClip.speed.coerceIn(0.25f, 4.0f)
        if (abs(videoPlayer.playbackParameters.speed - targetSpeed) > 0.01f) {
            videoPlayer.playbackParameters = PlaybackParameters(targetSpeed)
        }
    }

    fun play(totalTimelineDurationMs: Long) {
        if (totalTimelineDurationMs <= 0L) return
        val currentPos = currentTimelinePositionMs()
        if (currentPos >= totalTimelineDurationMs - 50L) {
            seekTo(0L)
        }

        lastDriftCorrectionRealtimeMs = SystemClock.elapsedRealtime()
        applyCurrentClipSpeed()
        val canPlayVideo = totalVideoDurationMs > 0L && (
            endBehavior == VideoEndBehavior.LOOP_TO_AUDIO ||
                currentTimelinePositionMs() < totalVideoDurationMs
            )
        if (canPlayVideo) {
            videoPlayer.play()
        }
        if (audioConfig.uri != null && currentTimelinePositionMs() < audioConfig.effectiveAudioDurationMs) {
            audioPlayer.play()
        }
    }

    fun pause() {
        videoPlayer.pause()
        audioPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        val targetMs = positionMs.coerceAtLeast(0L)
        lastDriftCorrectionRealtimeMs = SystemClock.elapsedRealtime()
        seekVideoOnly(targetMs)

        if (audioConfig.uri != null && audioConfig.effectiveAudioDurationMs > 0L) {
            if (targetMs >= audioConfig.effectiveAudioDurationMs) {
                audioPlayer.seekTo(audioConfig.effectiveAudioDurationMs)
                audioPlayer.pause()
            } else {
                audioPlayer.seekTo(targetMs)
            }
        }

        applyCurrentClipSpeed()
        applyVolumes(targetMs)
    }

    private fun seekVideoOnly(targetMs: Long) {
        if (clipPlayedDurationsMs.isEmpty() || totalVideoDurationMs <= 0L) return

        if (targetMs >= totalVideoDurationMs && endBehavior == VideoEndBehavior.FREEZE_FRAME) {
            val lastIndex = clipPlayedDurationsMs.lastIndex
            val lastClip = expandedClipsCache.getOrNull(lastIndex)
            val speed = lastClip?.speed?.coerceIn(0.25f, 4.0f) ?: 1f
            val rawEndOffset = ((clipPlayedDurationsMs[lastIndex] * speed).toLong() - 40L)
                .coerceAtLeast(0L)
            videoPlayer.seekTo(lastIndex, rawEndOffset)
            videoPlayer.pause()
        } else {
            val mappedVideoPos = if (endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
                targetMs % totalVideoDurationMs
            } else {
                targetMs
            }
            var accumulated = 0L
            for (i in clipPlayedDurationsMs.indices) {
                val playedDur = clipPlayedDurationsMs[i]
                if (mappedVideoPos < accumulated + playedDur || i == clipPlayedDurationsMs.lastIndex) {
                    val speed = expandedClipsCache.getOrNull(i)?.speed?.coerceIn(0.25f, 4.0f) ?: 1f
                    val localPlayedPos = (mappedVideoPos - accumulated).coerceIn(0L, playedDur)
                    val localRawPos = (localPlayedPos * speed).toLong()
                    videoPlayer.seekTo(i, localRawPos)
                    break
                }
                accumulated += playedDur
            }
        }
    }

    fun tickSync(isPlaying: Boolean, totalTimelineDurationMs: Long): Long {
        applyCurrentClipSpeed()
        val hasAudio = audioConfig.uri != null && audioConfig.effectiveAudioDurationMs > 0L
        val pos = currentTimelinePositionMs().coerceIn(0L, totalTimelineDurationMs)
        applyVolumes(pos)

        if (isPlaying) {
            val nowMs = SystemClock.elapsedRealtime()
            val canCorrectDrift = (nowMs - lastDriftCorrectionRealtimeMs) > 950L

            if (canCorrectDrift &&
                hasAudio &&
                totalVideoDurationMs > 0L &&
                pos < audioConfig.effectiveAudioDurationMs &&
                videoPlayer.playbackState == Player.STATE_READY
            ) {
                val expectedVideoPos = if (endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
                    pos % totalVideoDurationMs
                } else {
                    pos.coerceAtMost(totalVideoDurationMs)
                }

                // Elakkan gangguan seek semasa ExoPlayer sedang melengkapkan peralihan sempadan loop
                val nearLoopBoundary = expectedVideoPos < 350L ||
                    (totalVideoDurationMs - expectedVideoPos) < 350L

                if (!nearLoopBoundary && (pos < totalVideoDurationMs || endBehavior == VideoEndBehavior.LOOP_TO_AUDIO)) {
                    val actualVideoPos = getRawVideoPositionMs()
                    if (abs(expectedVideoPos - actualVideoPos) > 400L) {
                        lastDriftCorrectionRealtimeMs = nowMs
                        seekVideoOnly(pos)
                        applyCurrentClipSpeed()
                        if (!videoPlayer.isPlaying) {
                            videoPlayer.play()
                        }
                    }
                }
            }

            if (endBehavior == VideoEndBehavior.FREEZE_FRAME &&
                totalVideoDurationMs > 0L &&
                pos >= totalVideoDurationMs &&
                videoPlayer.isPlaying
            ) {
                videoPlayer.pause()
            }
            if (hasAudio &&
                pos >= audioConfig.effectiveAudioDurationMs &&
                audioPlayer.isPlaying
            ) {
                audioPlayer.pause()
            }
        }
        return pos
    }

    private fun getRawVideoPositionMs(): Long {
        if (clipPlayedDurationsMs.isEmpty() || totalVideoDurationMs <= 0L) return 0L
        val idx = videoPlayer.currentMediaItemIndex.coerceIn(0, clipPlayedDurationsMs.lastIndex)
        var before = 0L
        for (i in 0 until idx) {
            before += clipPlayedDurationsMs[i]
        }
        val speed = expandedClipsCache.getOrNull(idx)?.speed?.coerceIn(0.25f, 4.0f) ?: 1f
        val currentClipPlayedMs = (videoPlayer.currentPosition.coerceAtLeast(0L) / speed).toLong()
        return (before + currentClipPlayedMs).coerceAtMost(totalVideoDurationMs)
    }

    fun currentTimelinePositionMs(): Long {
        val audioPos = if (audioConfig.uri != null && audioConfig.effectiveAudioDurationMs > 0L) {
            audioPlayer.currentPosition.coerceAtLeast(0L)
        } else {
            0L
        }

        val videoPos = getRawVideoPositionMs()

        return if (audioConfig.uri != null && audioConfig.effectiveAudioDurationMs > 0L) {
            if (audioPos < audioConfig.effectiveAudioDurationMs - 50L) {
                audioPos
            } else {
                maxOf(videoPos, audioPos)
            }
        } else {
            videoPos
        }
    }

    private fun applyVolumes(positionMs: Long) {
        val vidVol = if (audioConfig.isOriginalAudioMuted) {
            0f
        } else {
            audioConfig.originalVideoVolume.coerceIn(0f, 1f)
        }
        videoPlayer.volume = vidVol

        val effectiveAudioDur = audioConfig.effectiveAudioDurationMs
        var musVol = audioConfig.musicVolume.coerceIn(0f, 1f)

        if (audioConfig.fadeInDurationMs > 0L && positionMs < audioConfig.fadeInDurationMs) {
            musVol *= (positionMs.toFloat() / audioConfig.fadeInDurationMs.toFloat()).coerceIn(0f, 1f)
        }
        if (audioConfig.fadeOutDurationMs > 0L && effectiveAudioDur > 0L) {
            val fadeStart = (effectiveAudioDur - audioConfig.fadeOutDurationMs).coerceAtLeast(0L)
            if (positionMs > fadeStart) {
                val rem = (effectiveAudioDur - positionMs).coerceAtLeast(0L)
                musVol *= (rem.toFloat() / audioConfig.fadeOutDurationMs.toFloat()).coerceIn(0f, 1f)
            }
        }
        audioPlayer.volume = musVol
    }

    fun release() {
        videoPlayer.release()
        audioPlayer.release()
    }
}
