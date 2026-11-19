package com.muzrc.klipsync.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BeatSensitivity(
    val label: String,
    val minIntervalMs: Long,
    val thresholdMultiplier: Float
) {
    LOW("Santai (Drop Utama)", 550L, 1.65f),
    MEDIUM("Sederhana (Normal)", 380L, 1.40f),
    HIGH("Rancak (Cepat)", 260L, 1.22f)
}

object BeatDetector {

    suspend fun detectBeats(
        context: Context,
        uri: Uri,
        startOffsetMs: Long = 0L,
        sensitivity: BeatSensitivity = BeatSensitivity.MEDIUM
    ): List<Long> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return@withContext emptyList()
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            } else {
                44100
            }
            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                2
            }

            if (startOffsetMs > 0L) {
                extractor.seekTo(startOffsetMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            // Tetingkap analisis tenaga setiap ~40ms
            val windowSamples = ((sampleRate * 40L) / 1000L).toInt().coerceAtLeast(256)
            val windowEnergies = mutableListOf<Pair<Long, Float>>()

            var currentWindowSumSq = 0.0
            var currentWindowCount = 0
            var totalFramesProcessed = 0L

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputDone = false
            var isOutputDone = false

            while (!isOutputDone) {
                if (!isInputDone) {
                    val inIndex = codec.dequeueInputBuffer(5000L)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isInputDone = true
                            } else {
                                val sampleTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                if (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val remainingShorts = shortBuffer.remaining()

                        var idx = 0
                        while (idx + channelCount <= remainingShorts) {
                            // Ambil saluran pertama (mono/kiri) untuk kelajuan analisis
                            val norm = shortBuffer.get(idx) / 32768.0
                            currentWindowSumSq += norm * norm
                            currentWindowCount++
                            totalFramesProcessed++

                            if (currentWindowCount >= windowSamples) {
                                val rms = sqrt(currentWindowSumSq / currentWindowCount).toFloat()
                                val timeMs = (totalFramesProcessed * 1000L) / sampleRate
                                windowEnergies.add(timeMs to rms)
                                currentWindowSumSq = 0.0
                                currentWindowCount = 0
                            }
                            idx += channelCount
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputDone = true
                    }
                }
            }

            if (windowEnergies.size < 6) return@withContext emptyList()

            // Kira Spectral/Energy Flux (lonjakan tenaga berbanding bingkai sebelumnya)
            val fluxList = FloatArray(windowEnergies.size)
            for (i in 1 until windowEnergies.size) {
                val diff = windowEnergies[i].second - windowEnergies[i - 1].second
                fluxList[i] = if (diff > 0f) diff else 0f
            }

            // Pilih puncak beat menggunakan ambang dinamik setempat (~0.8s sekitar)
            val detectedBeats = mutableListOf<Long>()
            val historyRadius = 10 // 10 * 40ms = 400ms kiri & kanan
            var lastAcceptedBeatMs = -sensitivity.minIntervalMs

            for (i in 2 until fluxList.size - 1) {
                val currentFlux = fluxList[i]
                if (currentFlux <= 0.008f) continue

                // Pastikan ia adalah puncak tempatan
                if (currentFlux < fluxList[i - 1] || currentFlux < fluxList[i + 1]) continue

                val startIdx = (i - historyRadius).coerceAtLeast(0)
                val endIdx = (i + historyRadius).coerceAtMost(fluxList.lastIndex)
                var sum = 0f
                var count = 0
                for (j in startIdx..endIdx) {
                    sum += fluxList[j]
                    count++
                }
                val localAvg = if (count > 0) sum / count else 0f
                val threshold = (localAvg * sensitivity.thresholdMultiplier).coerceAtLeast(0.015f)

                val candidateTimeMs = windowEnergies[i].first
                if (currentFlux >= threshold &&
                    candidateTimeMs >= 200L &&
                    (candidateTimeMs - lastAcceptedBeatMs) >= sensitivity.minIntervalMs
                ) {
                    detectedBeats.add(candidateTimeMs)
                    lastAcceptedBeatMs = candidateTimeMs
                }
            }

            detectedBeats
        } catch (_: Exception) {
            emptyList()
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }
}
