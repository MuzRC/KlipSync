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
import com.muzrc.klipsync.model.CameraMotionEffect
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

data class PreviewClipMotionVisual(
    val zoomScale: Float = 1f,
    val panOffsetXFraction: Float = 0f
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
    private var videoItemIndexMap: List<Int> = emptyList()
    private var totalVideoDurationMs: Long = 0L
    private var audioConfig: AudioTrackConfig = AudioTrackConfig()
    private var endBehavior: VideoEndBehavior = VideoEndBehavior.FREEZE_FRAME
    private var lastVideoSignature: String = ""
    private var lastAudioSignature: String = ""
    private var lastDriftCorrectionRealtimeMs: Long = 0L

    private var standaloneClockBasePositionMs: Long = 0L
    private var standaloneClockStartRealtimeMs: Long = 0L

    fun syncMedia(
        clips: List<VideoClip>,
        transitionConfig: TransitionConfig,
        audio: AudioTrackConfig
    ) {
        this.audioConfig = audio
        this.endBehavior = transitionConfig.endBehavior

        // Sentiasa kemas kini cache klip supaya perubahan Zoom/Pan, Putaran & Cermin terus aktif tanpa reset pemain
        expandedClipsCache = clips.flatMap { clip ->
            List(clip.loopCount.coerceAtLeast(1)) { clip }
        }

        val videoSignature = buildString {
            append(transitionConfig.joinMode.name).append(":")
            append(transitionConfig.durationMs).append(":")
            append(transitionConfig.endBehavior.name).append("|")
            clips.forEach {
                append("${it.id}:${it.uri}:${it.trimStartMs}:${it.trimEndMs}:${it.loopCount}:${it.speed}:${it.isImage};")
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
            videoItemIndexMap = emptyList()
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
        val indexMap = mutableListOf<Int>()

        expandedClips.forEachIndexed { index, clip ->
            val isFirst = index == 0
            val isLast = index == expandedClips.lastIndex
            val safeSpeed = if (clip.isImage) 1.0f else clip.speed.coerceIn(0.25f, 4.0f)

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

            if (clip.isImage) {
                indexMap.add(-1)
            } else {
                indexMap.add(items.size)
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
        }

        clipPlayedDurationsMs = playedDurations
        videoItemIndexMap = indexMap
        totalVideoDurationMs = playedDurations.sum()

        if (items.isNotEmpty()) {
            videoPlayer.repeatMode = if (transitionConfig.endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
                Player.REPEAT_MODE_ALL
            } else {
                Player.REPEAT_MODE_OFF
            }
            videoPlayer.setMediaItems(items)
            videoPlayer.prepare()
        }
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

    private fun findSegmentIndexAt(positionMs: Long): Int {
        if (clipPlayedDurationsMs.isEmpty() || totalVideoDurationMs <= 0L) return -1
        val mappedPos = if (endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
            positionMs % totalVideoDurationMs
        } else {
            positionMs.coerceIn(0L, totalVideoDurationMs)
        }
        var acc = 0L
        for (i in clipPlayedDurationsMs.indices) {
            val dur = clipPlayedDurationsMs[i]
            if (mappedPos < acc + dur || i == clipPlayedDurationsMs.lastIndex) {
                return i
            }
            acc += dur
        }
        return clipPlayedDurationsMs.lastIndex
    }

    fun currentActiveClipAt(positionMs: Long): VideoClip? {
        val segIdx = findSegmentIndexAt(positionMs)
        if (segIdx in expandedClipsCache.indices) {
            return expandedClipsCache[segIdx]
        }
        return expandedClipsCache.firstOrNull()
    }

    fun computeClipMotionVisual(positionMs: Long): PreviewClipMotionVisual {
        if (clipPlayedDurationsMs.isEmpty() || totalVideoDurationMs <= 0L) {
            return PreviewClipMotionVisual()
        }
        if (endBehavior == VideoEndBehavior.FREEZE_FRAME && positionMs >= totalVideoDurationMs) {
            return PreviewClipMotionVisual()
        }

        val mappedPos = if (endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
            positionMs % totalVideoDurationMs
        } else {
            positionMs.coerceIn(0L, totalVideoDurationMs)
        }

        var acc = 0L
        for (i in clipPlayedDurationsMs.indices) {
            val dur = clipPlayedDurationsMs[i]
            if (mappedPos < acc + dur || i == clipPlayedDurationsMs.lastIndex) {
                val clip = expandedClipsCache.getOrNull(i) ?: return PreviewClipMotionVisual()
                val localPos = (mappedPos - acc).coerceIn(0L, dur)
                val progress = if (dur > 0L) {
                    (localPos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                val intensity = clip.motionIntensity.coerceIn(0.05f, 0.50f)
                return when (clip.motionEffect) {
                    CameraMotionEffect.NONE -> PreviewClipMotionVisual()
                    CameraMotionEffect.ZOOM_IN -> PreviewClipMotionVisual(
                        zoomScale = 1.0f + (intensity * progress)
                    )
                    CameraMotionEffect.ZOOM_OUT -> PreviewClipMotionVisual(
                        zoomScale = (1.0f + intensity) - (intensity * progress)
                    )
                    CameraMotionEffect.PAN_LEFT -> {
                        val baseZoom = 1.0f + intensity
                        val maxShift = (intensity * 0.42f)
                        PreviewClipMotionVisual(
                            zoomScale = baseZoom,
                            panOffsetXFraction = maxShift - (2f * maxShift * progress)
                        )
                    }
                    CameraMotionEffect.PAN_RIGHT -> {
                        val baseZoom = 1.0f + intensity
                        val maxShift = (intensity * 0.42f)
                        PreviewClipMotionVisual(
                            zoomScale = baseZoom,
                            panOffsetXFraction = -maxShift + (2f * maxShift * progress)
                        )
                    }
                }
            }
            acc += dur
        }
        return PreviewClipMotionVisual()
    }

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
        val activeClip = currentActiveClipAt(currentTimelinePositionMs()) ?: return
        if (activeClip.isImage) return
        val targetSpeed = activeClip.speed.coerceIn(0.25f, 4.0f)
        if (abs(videoPlayer.playbackParameters.speed - targetSpeed) > 0.01f) {
            videoPlayer.playbackParameters = PlaybackParameters(targetSpeed)
        }
    }

    private fun hasAnyVideoMedia(): Boolean = videoItemIndexMap.any { it >= 0 }

    fun play(totalTimelineDurationMs: Long) {
        if (totalTimelineDurationMs <= 0L) return
        val currentPos = currentTimelinePositionMs()
        if (currentPos >= totalTimelineDurationMs - 50L) {
            seekTo(0L)
        }

        val startPos = currentTimelinePositionMs()
        standaloneClockBasePositionMs = startPos
        standaloneClockStartRealtimeMs = SystemClock.elapsedRealtime()
        lastDriftCorrectionRealtimeMs = standaloneClockStartRealtimeMs

        val activeClip = currentActiveClipAt(startPos)
        if (hasAnyVideoMedia() && activeClip != null && !activeClip.isImage) {
            applyCurrentClipSpeed()
            val canPlayVideo = totalVideoDurationMs > 0L && (
                endBehavior == VideoEndBehavior.LOOP_TO_AUDIO ||
                    startPos < totalVideoDurationMs
                )
            if (canPlayVideo) {
                videoPlayer.play()
            }
        } else {
            videoPlayer.pause()
        }

        if (audioConfig.uri != null && startPos < audioConfig.effectiveAudioDurationMs) {
            audioPlayer.play()
        }
    }

    fun pause() {
        standaloneClockBasePositionMs = currentTimelinePositionMs()
        standaloneClockStartRealtimeMs = 0L
        videoPlayer.pause()
        audioPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        val targetMs = positionMs.coerceAtLeast(0L)
        standaloneClockBasePositionMs = targetMs
        if (standaloneClockStartRealtimeMs > 0L) {
            standaloneClockStartRealtimeMs = SystemClock.elapsedRealtime()
        }
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
        if (clipPlayedDurationsMs.isEmpty() || totalVideoDurationMs <= 0L || !hasAnyVideoMedia()) return

        val mappedVideoPos = if (endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
            targetMs % totalVideoDurationMs
        } else {
            targetMs.coerceAtMost(totalVideoDurationMs)
        }

        var accumulated = 0L
        for (i in clipPlayedDurationsMs.indices) {
            val playedDur = clipPlayedDurationsMs[i]
            if (mappedVideoPos < accumulated + playedDur || i == clipPlayedDurationsMs.lastIndex) {
                val playerIdx = videoItemIndexMap[i]
                if (playerIdx >= 0) {
                    val speed = expandedClipsCache.getOrNull(i)?.speed?.coerceIn(0.25f, 4.0f) ?: 1f
                    val localPlayedPos = (mappedVideoPos - accumulated).coerceIn(0L, playedDur)
                    val localRawPos = (localPlayedPos * speed).toLong()
                    videoPlayer.seekTo(playerIdx, localRawPos)
                } else {
                    videoPlayer.pause()
                }
                break
            }
            accumulated += playedDur
        }
    }

    fun tickSync(isPlaying: Boolean, totalTimelineDurationMs: Long): Long {
        val hasAudio = audioConfig.uri != null && audioConfig.effectiveAudioDurationMs > 0L
        val pos = currentTimelinePositionMs().coerceIn(0L, totalTimelineDurationMs)
        applyVolumes(pos)

        if (isPlaying) {
            val activeClip = currentActiveClipAt(pos)
            val isImageSegment = activeClip?.isImage == true

            if (isImageSegment) {
                if (videoPlayer.isPlaying) {
                    videoPlayer.pause()
                }
            } else if (hasAnyVideoMedia()) {
                applyCurrentClipSpeed()
                val inVideoRange = pos < totalVideoDurationMs || endBehavior == VideoEndBehavior.LOOP_TO_AUDIO
                if (inVideoRange && !videoPlayer.isPlaying && videoPlayer.playbackState == Player.STATE_READY) {
                    seekVideoOnly(pos)
                    videoPlayer.play()
                }

                val nowMs = SystemClock.elapsedRealtime()
                val canCorrectDrift = (nowMs - lastDriftCorrectionRealtimeMs) > 950L
                if (canCorrectDrift && hasAudio && pos < audioConfig.effectiveAudioDurationMs) {
                    val expectedVideoPos = if (endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
                        pos % totalVideoDurationMs
                    } else {
                        pos.coerceAtMost(totalVideoDurationMs)
                    }
                    val nearBoundary = expectedVideoPos < 350L || (totalVideoDurationMs - expectedVideoPos) < 350L
                    if (!nearBoundary && inVideoRange) {
                        val actualVideoPos = getRawVideoPositionMs()
                        if (abs(expectedVideoPos - actualVideoPos) > 400L) {
                            lastDriftCorrectionRealtimeMs = nowMs
                            seekVideoOnly(pos)
                            if (!videoPlayer.isPlaying) {
                                videoPlayer.play()
                            }
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
        if (!hasAnyVideoMedia() || clipPlayedDurationsMs.isEmpty() || totalVideoDurationMs <= 0L) return 0L
        val currentPlayerIdx = videoPlayer.currentMediaItemIndex
        val segIdx = videoItemIndexMap.indexOf(currentPlayerIdx)
        if (segIdx == -1) return 0L

        var before = 0L
        for (i in 0 until segIdx) {
            before += clipPlayedDurationsMs[i]
        }
        val speed = expandedClipsCache.getOrNull(segIdx)?.speed?.coerceIn(0.25f, 4.0f) ?: 1f
        val currentClipPlayedMs = (videoPlayer.currentPosition.coerceAtLeast(0L) / speed).toLong()
        return (before + currentClipPlayedMs).coerceAtMost(totalVideoDurationMs)
    }

    fun currentTimelinePositionMs(): Long {
        if (audioConfig.uri != null && audioConfig.effectiveAudioDurationMs > 0L) {
            val audioPos = audioPlayer.currentPosition.coerceAtLeast(0L)
            if (audioPos < audioConfig.effectiveAudioDurationMs - 50L) {
                return audioPos
            }
        }

        if (standaloneClockStartRealtimeMs > 0L) {
            val elapsed = (SystemClock.elapsedRealtime() - standaloneClockStartRealtimeMs).coerceAtLeast(0L)
            return standaloneClockBasePositionMs + elapsed
        }

        return if (hasAnyVideoMedia()) {
            maxOf(standaloneClockBasePositionMs, getRawVideoPositionMs())
        } else {
            standaloneClockBasePositionMs
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
