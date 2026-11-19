package com.muzrc.klipsync.preview

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.muzrc.klipsync.model.AudioTrackConfig
import com.muzrc.klipsync.model.JoinMode
import com.muzrc.klipsync.model.TransitionConfig
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.model.VideoEndBehavior
import kotlin.math.abs

@OptIn(UnstableApi::class)
class PreviewPlayerController(context: Context) {

    val videoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
    }

    val audioPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
    }

    private var clipDurationsMs: List<Long> = emptyList()
    private var totalVideoDurationMs: Long = 0L
    private var audioConfig: AudioTrackConfig = AudioTrackConfig()
    private var endBehavior: VideoEndBehavior = VideoEndBehavior.FREEZE_FRAME
    private var lastVideoSignature: String = ""
    private var lastAudioSignature: String = ""

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
                append("${it.id}:${it.uri}:${it.trimStartMs}:${it.trimEndMs}:${it.loopCount};")
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

        applyVolumes(currentTimelinePositionMs())
    }

    private fun rebuildVideoPlaylist(
        clips: List<VideoClip>,
        transitionConfig: TransitionConfig
    ) {
        videoPlayer.stop()
        videoPlayer.clearMediaItems()

        if (clips.isEmpty()) {
            clipDurationsMs = emptyList()
            totalVideoDurationMs = 0L
            return
        }

        val expandedClips = clips.flatMap { clip ->
            List(clip.loopCount.coerceAtLeast(1)) { clip }
        }

        val overlapHalfMs = if (transitionConfig.joinMode == JoinMode.OVERLAP && expandedClips.size > 1) {
            (transitionConfig.durationMs / 2L).coerceAtLeast(0L)
        } else {
            0L
        }

        val durations = mutableListOf<Long>()
        val items = mutableListOf<MediaItem>()

        expandedClips.forEachIndexed { index, clip ->
            val isFirst = index == 0
            val isLast = index == expandedClips.lastIndex

            val start = if (!isFirst) {
                (clip.trimStartMs + overlapHalfMs).coerceAtMost(clip.trimEndMs)
            } else {
                clip.trimStartMs
            }

            val end = if (!isLast) {
                (clip.trimEndMs - overlapHalfMs).coerceAtLeast(start)
            } else {
                clip.trimEndMs
            }

            durations.add((end - start).coerceAtLeast(0L))

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

        clipDurationsMs = durations
        totalVideoDurationMs = durations.sum()
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

    fun play(totalTimelineDurationMs: Long) {
        if (totalTimelineDurationMs <= 0L) return
        val currentPos = currentTimelinePositionMs()
        if (currentPos >= totalTimelineDurationMs - 50L) {
            seekTo(0L)
        }

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
        seekVideoOnly(targetMs)

        if (audioConfig.uri != null && audioConfig.effectiveAudioDurationMs > 0L) {
            if (targetMs >= audioConfig.effectiveAudioDurationMs) {
                audioPlayer.seekTo(audioConfig.effectiveAudioDurationMs)
                audioPlayer.pause()
            } else {
                audioPlayer.seekTo(targetMs)
            }
        }

        applyVolumes(targetMs)
    }

    private fun seekVideoOnly(targetMs: Long) {
        if (clipDurationsMs.isEmpty() || totalVideoDurationMs <= 0L) return

        if (targetMs >= totalVideoDurationMs && endBehavior == VideoEndBehavior.FREEZE_FRAME) {
            val lastIndex = clipDurationsMs.lastIndex
            val lastClipEndOffset = (clipDurationsMs[lastIndex] - 40L).coerceAtLeast(0L)
            videoPlayer.seekTo(lastIndex, lastClipEndOffset)
            videoPlayer.pause()
        } else {
            val mappedVideoPos = if (endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
                targetMs % totalVideoDurationMs
            } else {
                targetMs
            }
            var accumulated = 0L
            for (i in clipDurationsMs.indices) {
                val dur = clipDurationsMs[i]
                if (mappedVideoPos < accumulated + dur || i == clipDurationsMs.lastIndex) {
                    val localPos = (mappedVideoPos - accumulated).coerceIn(0L, dur)
                    videoPlayer.seekTo(i, localPos)
                    break
                }
                accumulated += dur
            }
        }
    }

    fun tickSync(isPlaying: Boolean, totalTimelineDurationMs: Long): Long {
        val hasAudio = audioConfig.uri != null && audioConfig.effectiveAudioDurationMs > 0L
        val pos = currentTimelinePositionMs().coerceIn(0L, totalTimelineDurationMs)
        applyVolumes(pos)

        if (isPlaying) {
            // Kunci sync: jadikan audio sebagai master clock jika lagu aktif
            if (hasAudio && totalVideoDurationMs > 0L && pos < audioConfig.effectiveAudioDurationMs) {
                val expectedVideoPos = if (endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
                    pos % totalVideoDurationMs
                } else {
                    pos.coerceAtMost(totalVideoDurationMs)
                }

                val actualVideoPos = getRawVideoPositionMs()
                // Jika video terlewat > 140ms semasa pertukaran klip, selaraskan semula serta-merta
                if (pos < totalVideoDurationMs || endBehavior == VideoEndBehavior.LOOP_TO_AUDIO) {
                    if (abs(expectedVideoPos - actualVideoPos) > 140L) {
                        seekVideoOnly(pos)
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
        if (clipDurationsMs.isEmpty() || totalVideoDurationMs <= 0L) return 0L
        val idx = videoPlayer.currentMediaItemIndex.coerceIn(0, clipDurationsMs.lastIndex)
        var before = 0L
        for (i in 0 until idx) {
            before += clipDurationsMs[i]
        }
        return (before + videoPlayer.currentPosition.coerceAtLeast(0L))
            .coerceAtMost(totalVideoDurationMs)
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
