package com.muzrc.klipsync.media

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.muzrc.klipsync.model.EditorUiState
import com.muzrc.klipsync.model.ExportState
import com.muzrc.klipsync.util.GallerySaver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@UnstableApi
class VideoExporter(private val context: Context) {

    fun exportProject(state: EditorUiState): Flow<ExportState> = channelFlow {
        send(ExportState.Exporting(0))

        if (state.clips.isEmpty()) {
            send(ExportState.Error("Sila pilih sekurang-kurangnya satu klip video dahulu."))
            return@channelFlow
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()

        val videoBuilder = VideoSequenceBuilder(context)
        val videoSequence = videoBuilder.buildVideoSequence(
            clips = state.clips,
            transitionConfig = state.transitionConfig,
            colorConfig = state.colorConfig,
            audioConfig = state.audioConfig,
            aspectRatio = state.aspectRatio,
            cropScaleMode = state.cropScaleMode,
            subtitles = state.subtitles,
            watermarkConfig = state.watermarkConfig,
            exportConfig = state.exportConfig,
            freezeFrameExtensionMs = state.freezeFrameExtensionMs,
            autoLoopExtensionMs = state.autoLoopExtensionMs
        )

        if (videoSequence == null) {
            send(ExportState.Error("Gagal menyediakan susunan video."))
            return@channelFlow
        }
        sequences.add(videoSequence)

        val musicSequence = AudioSequenceBuilder.buildMusicSequence(state.audioConfig)
        if (musicSequence != null) {
            sequences.add(musicSequence)
        }

        val composition = Composition.Builder(sequences)
            .experimentalSetForceAudioTrack(musicSequence != null || !state.audioConfig.isOriginalAudioMuted)
            .build()

        val tempOutputFile = File(
            context.cacheDir,
            "klipsync_export_${System.currentTimeMillis()}.mp4"
        )

        try {
            val exportSuccess = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Result<Unit>> { continuation ->
                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult
                            ) {
                                if (continuation.isActive) {
                                    continuation.resume(Result.success(Unit))
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(exportException))
                                }
                            }
                        })
                        .build()

                    val progressHolder = ProgressHolder()
                    val progressJob = launch {
                        while (isActive) {
                            val progressState = transformer.getProgress(progressHolder)
                            if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                                trySend(ExportState.Exporting(progressHolder.progress.coerceIn(0, 99)))
                            }
                            delay(250L)
                        }
                    }

                    continuation.invokeOnCancellation {
                        progressJob.cancel()
                        transformer.cancel()
                    }

                    transformer.start(composition, tempOutputFile.absolutePath)
                }
            }

            if (exportSuccess.isSuccess && tempOutputFile.exists()) {
                send(ExportState.Exporting(99))
                val savedUri = GallerySaver.saveMp4ToGallery(
                    context = context,
                    sourceFile = tempOutputFile,
                    displayName = "KlipSync_${System.currentTimeMillis()}.mp4"
                )
                if (savedUri != null) {
                    send(ExportState.Success(savedUri.toString()))
                } else {
                    send(ExportState.Error("Video siap diproses tetapi gagal disimpan ke Galeri."))
                }
            } else {
                val reason = exportSuccess.exceptionOrNull()?.localizedMessage
                    ?: "Ralat tidak diketahui semasa mengeksport video."
                send(ExportState.Error(reason))
            }
        } finally {
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
            cleanupTempFreezeFrames()
        }
    }

    private fun cleanupTempFreezeFrames() {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("freeze_frame_") && file.name.endsWith(".png")) {
                file.delete()
            }
        }
    }
}
