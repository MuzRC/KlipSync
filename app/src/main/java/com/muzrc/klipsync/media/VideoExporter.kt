package com.muzrc.klipsync.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.muzrc.klipsync.model.EditorUiState
import com.muzrc.klipsync.model.ExportState
import com.muzrc.klipsync.model.VideoClip
import com.muzrc.klipsync.util.ExportDebugLogger
import com.muzrc.klipsync.util.GallerySaver
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

@UnstableApi
class VideoExporter(private val context: Context) {

    fun exportProject(state: EditorUiState): Flow<ExportState> = channelFlow {
        ExportDebugLogger.init(context)
        ExportDebugLogger.log("EXPORT", "=== MULA EKSPORT BAHARU ===")
        ExportDebugLogger.log("EXPORT", ExportDebugLogger.deviceHeader())
        ExportDebugLogger.log(
            "CONFIG",
            "Res=${state.exportConfig.resolution.name}, FPS=${state.exportConfig.frameRate.fps}, " +
                "Ratio=${state.aspectRatio.name}, ScaleMode=${state.cropScaleMode.name}, " +
                "Join=${state.transitionConfig.joinMode.name}, Trans=${state.transitionConfig.transitionType.name}, " +
                "EndBehavior=${state.transitionConfig.endBehavior.name}, FreezeMs=${state.freezeFrameExtensionMs}, " +
                "AutoLoopMs=${state.autoLoopExtensionMs}"
        )

        send(ExportState.Exporting(1))

        if (state.clips.isEmpty()) {
            ExportDebugLogger.logError("EXPORT", "Tiada klip video atau gambar dipilih.")
            send(ExportState.Error("Sila pilih sekurang-kurangnya satu klip video atau gambar dahulu."))
            return@channelFlow
        }

        val tempOutputFile = File(
            context.cacheDir,
            "klipsync_export_${System.currentTimeMillis()}.mp4"
        )

        try {
            val shortEdge = state.exportConfig.resolution.shortEdgePx
            ExportDebugLogger.log("STEP", "Memampatkan gambar input (shortEdge=$shortEdge)...")
            val preparedClips = prepareCompressedClipsForExport(state.clips, shortEdge)
            val safeState = state.copy(clips = preparedClips)

            System.gc()
            send(ExportState.Exporting(3))

            val sequences = mutableListOf<EditedMediaItemSequence>()

            ExportDebugLogger.log("STEP", "Membina VideoSequence...")
            val videoBuilder = VideoSequenceBuilder(context)
            val videoSequence = videoBuilder.buildVideoSequence(
                clips = safeState.clips,
                transitionConfig = safeState.transitionConfig,
                colorConfig = safeState.colorConfig,
                audioConfig = safeState.audioConfig,
                aspectRatio = safeState.aspectRatio,
                cropScaleMode = safeState.cropScaleMode,
                subtitles = safeState.subtitles,
                watermarkConfig = safeState.watermarkConfig,
                exportConfig = safeState.exportConfig,
                freezeFrameExtensionMs = safeState.freezeFrameExtensionMs,
                autoLoopExtensionMs = safeState.autoLoopExtensionMs
            )

            if (videoSequence == null) {
                ExportDebugLogger.logError("STEP", "buildVideoSequence memulangkan null.")
                send(ExportState.Error("Gagal menyediakan susunan visual untuk eksport."))
                return@channelFlow
            }
            sequences.add(videoSequence)

            ExportDebugLogger.log("STEP", "Membina AudioSequence...")
            val musicSequence = AudioSequenceBuilder.buildMusicSequence(safeState.audioConfig)
            if (musicSequence != null) {
                sequences.add(musicSequence)
            }

            val hasOriginalVideoAudio = !safeState.audioConfig.isOriginalAudioMuted &&
                safeState.clips.any { !it.isImage }
            val shouldForceAudioTrack = (musicSequence == null && hasOriginalVideoAudio)

            ExportDebugLogger.log(
                "COMPOSITION",
                "sequences=${sequences.size}, hasMusic=${musicSequence != null}, forceAudioTrack=$shouldForceAudioTrack"
            )

            val composition = Composition.Builder(sequences)
                .experimentalSetForceAudioTrack(shouldForceAudioTrack)
                .build()

            val targetBitrate = if (shortEdge >= 1080) 5_500_000 else 3_200_000
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setEnableFallback(true)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(targetBitrate)
                        .build()
                )
                .build()

            // Sediakan DataSourceBitmapLoader secara eksplisit supaya ImageAssetLoader tidak gagal checkState(bitmapLoader != null)
            val assetLoaderFactory = DefaultAssetLoaderFactory(
                context,
                DefaultDecoderFactory.Builder(context).build(),
                false,
                Looper.getMainLooper(),
                DataSourceBitmapLoader(context)
            )

            ExportDebugLogger.log("TRANSFORMER", "Memulakan Transformer.start()...")
            val exportSuccess = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Result<Unit>> { continuation ->
                    var transformerRef: Transformer? = null
                    var progressJob: Job? = null
                    try {
                        val transformer = Transformer.Builder(context)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .setAssetLoaderFactory(assetLoaderFactory)
                            .setEncoderFactory(encoderFactory)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: ExportResult
                                ) {
                                    progressJob?.cancel()
                                    ExportDebugLogger.log(
                                        "TRANSFORMER",
                                        "onCompleted! durationMs=${exportResult.durationMs}, fileSize=${exportResult.fileSizeBytes}"
                                    )
                                    if (continuation.isActive) {
                                        continuation.resume(Result.success(Unit))
                                    }
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    progressJob?.cancel()
                                    ExportDebugLogger.logError(
                                        "TRANSFORMER",
                                        "onError code=${exportException.errorCode} (${exportException.errorCodeName})",
                                        exportException
                                    )
                                    if (continuation.isActive) {
                                        continuation.resume(Result.failure(exportException))
                                    }
                                }
                            })
                            .build()

                        transformerRef = transformer

                        val progressHolder = ProgressHolder()
                        progressJob = launch {
                            var lastReportedPercent = 4
                            var lastProgressChangeRealtimeMs = SystemClock.elapsedRealtime()
                            var lastFileSizeBytes = 0L

                            while (isActive) {
                                val now = SystemClock.elapsedRealtime()
                                var currentPercent = lastReportedPercent

                                try {
                                    val progressState = transformer.getProgress(progressHolder)
                                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                                        val p = progressHolder.progress.coerceIn(4, 98)
                                        if (p > currentPercent) {
                                            currentPercent = p
                                            lastProgressChangeRealtimeMs = now
                                        }
                                    }
                                } catch (_: Throwable) {
                                }

                                val curSize = if (tempOutputFile.exists()) tempOutputFile.length() else 0L
                                if (curSize > lastFileSizeBytes) {
                                    lastFileSizeBytes = curSize
                                    lastProgressChangeRealtimeMs = now
                                    if (currentPercent < 15) {
                                        currentPercent = (currentPercent + 1).coerceAtMost(15)
                                    }
                                }

                                if (currentPercent != lastReportedPercent) {
                                    lastReportedPercent = currentPercent
                                    trySend(ExportState.Exporting(lastReportedPercent))
                                } else if (lastReportedPercent < 8 && (now - lastProgressChangeRealtimeMs) > 2000L) {
                                    lastReportedPercent += 1
                                    trySend(ExportState.Exporting(lastReportedPercent))
                                }

                                if ((now - lastProgressChangeRealtimeMs) > 45_000L) {
                                    ExportDebugLogger.logError("WATCHDOG", "Timeout > 45s pada $lastReportedPercent%, fileSize=$curSize")
                                    try {
                                        transformer.cancel()
                                    } catch (_: Throwable) {
                                    }
                                    if (continuation.isActive) {
                                        continuation.resume(
                                            Result.failure(
                                                IllegalStateException(
                                                    "Pengekod video telefon terhenti (timeout). Cuba tukar resolusi ke 720p HD atau 24 FPS."
                                                )
                                            )
                                        )
                                    }
                                    break
                                }

                                delay(400L)
                            }
                        }

                        continuation.invokeOnCancellation {
                            ExportDebugLogger.log("EXPORT", "Eksport dibatalkan oleh pengguna.")
                            progressJob?.cancel()
                            try {
                                transformerRef?.cancel()
                            } catch (_: Throwable) {
                            }
                        }

                        trySend(ExportState.Exporting(4))
                        transformer.start(composition, tempOutputFile.absolutePath)
                    } catch (t: Throwable) {
                        progressJob?.cancel()
                        ExportDebugLogger.logError("TRANSFORMER_INIT", "Pengecualian semasa memulakan Transformer", t)
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(t))
                        }
                    }
                }
            }

            if (exportSuccess.isSuccess && tempOutputFile.exists() && tempOutputFile.length() > 0L) {
                send(ExportState.Exporting(99))
                val savedUri = GallerySaver.saveMp4ToGallery(
                    context = context,
                    sourceFile = tempOutputFile,
                    displayName = "KlipSync_${System.currentTimeMillis()}.mp4"
                )
                if (savedUri != null) {
                    ExportDebugLogger.log("EXPORT", "Berjaya disimpan ke Galeri: $savedUri")
                    send(ExportState.Success(savedUri.toString()))
                } else {
                    ExportDebugLogger.logError("GALLERY", "GallerySaver.saveMp4ToGallery memulangkan null.")
                    send(ExportState.Error("Video siap diproses tetapi gagal disimpan ke Galeri."))
                }
            } else {
                val err = exportSuccess.exceptionOrNull()
                if (err is OutOfMemoryError || err?.cause is OutOfMemoryError) {
                    send(ExportState.Error("Memori telefon tidak mencukupi untuk resolusi ini. Sila pilih 720p HD dan cuba semula."))
                } else {
                    val targetErr = err?.cause ?: err
                    val msg = targetErr?.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: targetErr?.javaClass?.simpleName
                        ?: "Ralat tidak diketahui"
                    val topFrame = targetErr?.stackTrace?.firstOrNull { frame ->
                        frame.className.contains("klipsync") || frame.className.contains("media3")
                    } ?: targetErr?.stackTrace?.firstOrNull()
                    val location = if (topFrame != null) {
                        " (${topFrame.className.substringAfterLast('.')}.${topFrame.methodName}:${topFrame.lineNumber})"
                    } else {
                        ""
                    }
                    send(ExportState.Error("Eksport gagal: $msg$location — semak tab Dev Log"))
                }
            }
        } catch (oom: OutOfMemoryError) {
            System.gc()
            ExportDebugLogger.logError("OOM", "OutOfMemoryError semasa eksport", oom)
            send(ExportState.Error("Memori telefon tidak mencukupi. Sila pilih resolusi 720p HD atau kurangkan bilangan gambar."))
        } catch (t: Throwable) {
            ExportDebugLogger.logError("FATAL", "Ralat luar jangka semasa eksport", t)
            val detail = t.localizedMessage ?: t.javaClass.simpleName
            send(ExportState.Error("Ralat eksport: $detail"))
        } finally {
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
            cleanupTempExportFiles()
        }
    }

    private suspend fun prepareCompressedClipsForExport(
        clips: List<VideoClip>,
        targetShortEdge: Int
    ): List<VideoClip> = withContext(Dispatchers.IO) {
        val maxLongEdge = if (targetShortEdge >= 1080) 1440 else 1280
        val cachedByUri = mutableMapOf<String, Uri>()

        clips.map { clip ->
            if (!clip.isImage) {
                clip
            } else {
                val key = clip.uri.toString()
                val safeUri = cachedByUri.getOrPut(key) {
                    compressImageToCacheFile(clip.uri, maxLongEdge) ?: clip.uri
                }
                clip.copy(
                    uri = safeUri,
                    mimeType = MimeTypes.IMAGE_JPEG
                )
            }
        }
    }

    private fun compressImageToCacheFile(uri: Uri, maxLongEdge: Int): Uri? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }

            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            var sampleSize = 1
            while ((rawW / sampleSize) > maxLongEdge * 2 || (rawH / sampleSize) > maxLongEdge * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val curW = decoded.width
            val curH = decoded.height
            val longest = max(curW, curH)
            val scaleFactor = if (longest > maxLongEdge) {
                maxLongEdge.toFloat() / longest.toFloat()
            } else {
                1.0f
            }

            val targetW = (((curW * scaleFactor).roundToInt() / 2) * 2).coerceAtLeast(2)
            val targetH = (((curH * scaleFactor).roundToInt() / 2) * 2).coerceAtLeast(2)

            val scaledBitmap = if (targetW != curW || targetH != curH) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val outFile = File(context.cacheDir, "export_img_${System.nanoTime()}.jpg")
            FileOutputStream(outFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            scaledBitmap.recycle()
            ExportDebugLogger.log("COMPRESS", "Gambar dimampatkan: ${rawW}x${rawH} -> ${targetW}x${targetH}")
            Uri.fromFile(outFile)
        } catch (t: Throwable) {
            ExportDebugLogger.logError("COMPRESS", "Gagal memampatkan gambar $uri", t)
            null
        }
    }

    private fun cleanupTempExportFiles() {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if ((file.name.startsWith("freeze_frame_") && file.name.endsWith(".png")) ||
                    (file.name.startsWith("export_img_") && file.name.endsWith(".jpg"))
                ) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }
}
