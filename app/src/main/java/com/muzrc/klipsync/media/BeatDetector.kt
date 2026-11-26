package com.muzrc.klipsync.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.muzrc.klipsync.nativecore.NativeCoreBridge
import com.muzrc.klipsync.util.ExportDebugLogger
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BeatSensitivity(
    val label: String,
    val minIntervalMs: Long,
    val thresholdMultiplier: Float
) {
    LOW("Santai (Drop Utama)", 1150L, 1.95f),
    MEDIUM("Sederhana (Normal)", 580L, 1.65f),
    HIGH("Rancak (Cepat)", 380L, 1.42f);

    internal val globalFloorRatio: Float
        get() = when (this) {
            LOW -> 0.52f
            MEDIUM -> 0.36f
            HIGH -> 0.24f
        }
}

object BeatDetector {

    private const val TAG = "BEAT_DETECT"
    private const val MAX_IDLE_ITERATIONS = 2000
    private const val BASS_CUTOFF_HZ = 150.0
    private const val WINDOW_MS = 20L
    private const val MIN_BEAT_TIME_MS = 180L
    private const val MAX_REASONABLE_BEATS = 100_000

    private data class BassEnvelope(
        val timesMs: LongArray,
        val onsetScores: FloatArray,
        val referencePeakScore: Float
    )

    private data class BeatCandidate(
        val timeMs: Long,
        val score: Float
    )

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
                val seekUs = if (startOffsetMs > Long.MAX_VALUE / 1000L) {
                    Long.MAX_VALUE
                } else {
                    startOffsetMs * 1000L
                }
                extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val chunks = ArrayList<ShortArray>(256)
            var totalShorts = 0L

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputDone = false
            var isOutputDone = false
            var idleIterations = 0

            while (!isOutputDone) {
                var progress = false

                if (!isInputDone) {
                    val inIndex = codec.dequeueInputBuffer(5000L)
                    if (inIndex >= 0) {
                        progress = true
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
                    progress = true
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val count = shortBuffer.remaining()
                        if (count > 0) {
                            val arr = ShortArray(count)
                            shortBuffer.get(arr)
                            chunks.add(arr)
                            totalShorts += count
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputDone = true
                    }
                }

                if (!progress) {
                    if (++idleIterations > MAX_IDLE_ITERATIONS) {
                        ExportDebugLogger.log(TAG, "Timeout selepas $idleIterations iterasi idle")
                        break
                    }
                } else {
                    idleIterations = 0
                }
            }

            if (totalShorts <= 0L) return@withContext emptyList()
            if (totalShorts > Int.MAX_VALUE) {
                ExportDebugLogger.log(TAG, "Audio terlalu panjang ($totalShorts shorts), dihentikan")
                return@withContext emptyList()
            }

            val fullPcm = ShortArray(totalShorts.toInt())
            var offset = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, fullPcm, offset, chunk.size)
                offset += chunk.size
            }
            chunks.clear()

            val bassEnvelope = buildLowPassBassEnvelope(fullPcm, sampleRate, channelCount)
                ?: return@withContext emptyList()

            if (NativeCoreBridge.isNativeLoaded) {
                val t0 = System.nanoTime()
                val nativeResult = NativeCoreBridge.detectBeatsSafe(
                    pcmSamples = fullPcm,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    minIntervalMs = (sensitivity.minIntervalMs * 0.65f).toLong().coerceAtLeast(220L),
                    thresholdMultiplier = sensitivity.thresholdMultiplier
                )
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000L

                if (nativeResult != null && nativeResult.isNotEmpty()) {
                    val filteredNative = filterNativeBeatsWithBassLogic(
                        rawBeatsMs = nativeResult,
                        envelope = bassEnvelope,
                        sensitivity = sensitivity,
                        startOffsetMs = startOffsetMs
                    )
                    ExportDebugLogger.log(
                        "RUST_DSP",
                        "Auto-Beat Rust selesai dalam ${elapsedMs}ms (${nativeResult.size} mentah -> ${filteredNative.size} ditapis)"
                    )
                    return@withContext filteredNative
                } else {
                    ExportDebugLogger.log(
                        "RUST_DSP",
                        "detectBeatsSafe gagal/kosong selepas ${elapsedMs}ms, fallback ke Kotlin"
                    )
                }
            }

            val fallbackBeats = detectBeatsKotlinFallback(bassEnvelope, sensitivity)
            ExportDebugLogger.log(
                TAG,
                "Auto-Beat Kotlin selesai (${fallbackBeats.size} beat ditapis)"
            )
            fallbackBeats
        } catch (t: Throwable) {
            ExportDebugLogger.log(TAG, "Gagal detect beat: ${t.message}")
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

    private fun buildLowPassBassEnvelope(
        pcm: ShortArray,
        sampleRate: Int,
        channelCount: Int
    ): BassEnvelope? {
        if (sampleRate <= 0 || channelCount <= 0) return null
        val windowSamples = ((sampleRate.toLong() * WINDOW_MS) / 1000L).toInt().coerceAtLeast(128)
        val totalFrames = pcm.size / channelCount
        val estimatedWindows = (totalFrames / windowSamples) + 1
        if (estimatedWindows < 6) return null

        val timesList = ArrayList<Long>(estimatedWindows)
        val rmsList = ArrayList<Float>(estimatedWindows)

        val rc = 1.0 / (2.0 * PI * BASS_CUTOFF_HZ)
        val dt = 1.0 / sampleRate.toDouble()
        val alpha = dt / (rc + dt)

        var lpState = 0.0
        var windowSumSq = 0.0
        var windowCount = 0
        var framesProcessed = 0L

        var idx = 0
        while (idx + channelCount <= pcm.size) {
            var monoSample = 0.0
            for (ch in 0 until channelCount) {
                monoSample += pcm[idx + ch] / 32768.0
            }
            monoSample /= channelCount

            lpState += alpha * (monoSample - lpState)
            windowSumSq += lpState * lpState
            windowCount++
            framesProcessed++

            if (windowCount >= windowSamples) {
                val rms = sqrt(windowSumSq / windowCount).toFloat()
                val timeMs = (framesProcessed * 1000L) / sampleRate
                timesList.add(timeMs)
                rmsList.add(rms)
                windowSumSq = 0.0
                windowCount = 0
            }
            idx += channelCount
        }

        if (windowCount > 0) {
            val rms = sqrt(windowSumSq / windowCount).toFloat()
            val timeMs = (framesProcessed * 1000L) / sampleRate
            timesList.add(timeMs)
            rmsList.add(rms)
        }

        val count = timesList.size
        if (count < 6) return null

        val timesArr = LongArray(count) { timesList[it] }
        val rmsArr = FloatArray(count) { rmsList[it] }
        val onsetScores = FloatArray(count)
        val positiveScores = FloatArray(count)
        var positiveCount = 0

        for (i in 1 until count) {
            val flux = max(0f, rmsArr[i] - rmsArr[i - 1])
            val score = (flux * 0.72f) + (rmsArr[i] * 0.28f)
            onsetScores[i] = score
            if (flux > 0f) {
                positiveScores[positiveCount++] = score
            }
        }

        if (positiveCount == 0) return null

        val sortedPositive = positiveScores.copyOf(positiveCount)
        sortedPositive.sort()
        val p85Index = ((sortedPositive.size - 1) * 0.85f).toInt()
            .coerceIn(0, sortedPositive.lastIndex)
        val referencePeak = sortedPositive[p85Index]
        if (referencePeak <= 1e-5f) return null

        return BassEnvelope(
            timesMs = timesArr,
            onsetScores = onsetScores,
            referencePeakScore = referencePeak
        )
    }

    private fun filterNativeBeatsWithBassLogic(
        rawBeatsMs: LongArray,
        envelope: BassEnvelope,
        sensitivity: BeatSensitivity,
        startOffsetMs: Long
    ): List<Long> {
        if (rawBeatsMs.isEmpty() || envelope.onsetScores.isEmpty()) return emptyList()

        var isSorted = true
        for (i in 1 until rawBeatsMs.size) {
            if (rawBeatsMs[i] < rawBeatsMs[i - 1]) {
                isSorted = false
                break
            }
        }
        if (!isSorted) {
            ExportDebugLogger.log(TAG, "Amaran: rawBeatsMs tidak menaik dari Rust")
        }

        val globalFloor = envelope.referencePeakScore * sensitivity.globalFloorRatio
        val candidates = ArrayList<BeatCandidate>(rawBeatsMs.size)

        for (beatMs in rawBeatsMs) {
            if (beatMs < MIN_BEAT_TIME_MS) continue
            val bassScore = lookupPeakBassScoreAround(envelope, beatMs, radiusWindows = 3)
            if (bassScore >= globalFloor) {
                candidates.add(BeatCandidate(timeMs = beatMs, score = bassScore))
            }
        }

        @Suppress("UNUSED_VARIABLE")
        val offsetForFuture = startOffsetMs

        return applyNonMaximumSuppression(candidates, sensitivity.minIntervalMs)
    }

    private fun detectBeatsKotlinFallback(
        envelope: BassEnvelope,
        sensitivity: BeatSensitivity
    ): List<Long> {
        val scores = envelope.onsetScores
        val times = envelope.timesMs
        if (scores.size < 4) return emptyList()

        val historyRadius = 18
        val globalFloor = envelope.referencePeakScore * sensitivity.globalFloorRatio
        val candidates = ArrayList<BeatCandidate>()

        for (i in 2 until scores.size - 1) {
            val currentScore = scores[i]
            if (currentScore < globalFloor) continue

            if (currentScore < scores[i - 1] || currentScore < scores[i + 1]) continue

            val startIdx = (i - historyRadius).coerceAtLeast(0)
            val endIdx = (i + historyRadius).coerceAtMost(scores.lastIndex)
            var sum = 0f
            var count = 0
            for (j in startIdx..endIdx) {
                sum += scores[j]
                count++
            }
            val localAvg = if (count > 0) sum / count else 0f
            val adaptiveThreshold = localAvg * sensitivity.thresholdMultiplier

            val candidateTimeMs = times[i]
            if (candidateTimeMs >= MIN_BEAT_TIME_MS && currentScore >= adaptiveThreshold) {
                candidates.add(BeatCandidate(timeMs = candidateTimeMs, score = currentScore))
            }
        }

        return applyNonMaximumSuppression(candidates, sensitivity.minIntervalMs)
    }

    private fun applyNonMaximumSuppression(
        candidates: List<BeatCandidate>,
        minIntervalMs: Long
    ): List<Long> {
        if (candidates.isEmpty()) return emptyList()
        if (minIntervalMs <= 0L) {
            return candidates.map { it.timeMs }.distinct().sorted()
        }

        val sortedByStrength = if (candidates.size > MAX_REASONABLE_BEATS) {
            ExportDebugLogger.log(
                TAG,
                "Calon beat terlalu banyak (${candidates.size}), dihadkan ke $MAX_REASONABLE_BEATS puncak terkuat"
            )
            candidates.sortedByDescending { it.score }.take(MAX_REASONABLE_BEATS)
        } else {
            candidates.sortedByDescending { it.score }
        }

        val acceptedTimes = ArrayList<Long>(sortedByStrength.size)

        for (candidate in sortedByStrength) {
            val idx = lowerBound(acceptedTimes, candidate.timeMs)
            val tooCloseRight = idx < acceptedTimes.size &&
                abs(acceptedTimes[idx] - candidate.timeMs) < minIntervalMs
            val tooCloseLeft = idx > 0 &&
                abs(acceptedTimes[idx - 1] - candidate.timeMs) < minIntervalMs

            if (!tooCloseRight && !tooCloseLeft) {
                acceptedTimes.add(idx, candidate.timeMs)
            }
        }

        return acceptedTimes
    }

    private fun lowerBound(sorted: List<Long>, target: Long): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < target) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun lookupPeakBassScoreAround(
        envelope: BassEnvelope,
        targetMs: Long,
        radiusWindows: Int
    ): Float {
        if (envelope.onsetScores.isEmpty()) return 0f
        val centerIdx = findClosestTimeIndex(envelope, targetMs)
        val start = (centerIdx - radiusWindows).coerceAtLeast(0)
        val end = (centerIdx + radiusWindows).coerceAtMost(envelope.onsetScores.lastIndex)

        var maxScore = 0f
        for (i in start..end) {
            if (envelope.onsetScores[i] > maxScore) {
                maxScore = envelope.onsetScores[i]
            }
        }
        return maxScore
    }

    private fun findClosestTimeIndex(envelope: BassEnvelope, targetMs: Long): Int {
        val times = envelope.timesMs
        if (times.isEmpty()) return 0
        var lo = 0
        var hi = times.size - 1
        if (targetMs <= times[0]) return 0
        if (targetMs >= times[hi]) return hi

        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                times[mid] == targetMs -> return mid
                times[mid] < targetMs -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        val right = lo.coerceAtMost(times.lastIndex)
        val left = (lo - 1).coerceAtLeast(0)
        val diffRight = times[right] - targetMs
        val diffLeft = targetMs - times[left]
        return if (diffRight <= diffLeft) right else left
    }
}
