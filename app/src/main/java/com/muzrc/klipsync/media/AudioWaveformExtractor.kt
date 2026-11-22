package com.muzrc.klipsync.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.muzrc.klipsync.nativecore.NativeCoreBridge
import com.muzrc.klipsync.util.ExportDebugLogger
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioWaveformExtractor {

    private const val TAG = "WAVEFORM"
    private const val MAX_IDLE_ITERATIONS = 2000

    suspend fun extractWaveformPeaks(
        context: Context,
        uri: Uri,
        targetBars: Int = 96
    ): List<Float> = withContext(Dispatchers.IO) {
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

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val safeBars = targetBars.coerceAtLeast(16)
            val chunks = ArrayList<ShortArray>(128)
            var totalShorts = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputDone = false
            var isOutputDone = false
            var idleIterations = 0

            // Nota: kita tidak guna seekTo dalam loop kerana ia menyebabkan
            // sampel tidak berterusan dan decoder state tidak konsisten.
            // Untuk waveform, kita proses semua sampel secara berurutan.
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
                        val remaining = shortBuffer.remaining()
                        if (remaining > 0) {
                            val arr = ShortArray(remaining)
                            shortBuffer.get(arr)
                            chunks.add(arr)
                            totalShorts += remaining
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputDone = true
                    }
                }

                // Timeout keseluruhan untuk elak hang
                if (!progress) {
                    if (++idleIterations > MAX_IDLE_ITERATIONS) {
                        ExportDebugLogger.log(TAG, "Timeout selepas $idleIterations iterasi idle")
                        break
                    }
                } else {
                    idleIterations = 0
                }
            }

            if (totalShorts <= 0) return@withContext emptyList()

            val combinedPcm = ShortArray(totalShorts)
            var offset = 0
            for (c in chunks) {
                System.arraycopy(c, 0, combinedPcm, offset, c.size)
                offset += c.size
            }
            chunks.clear()

            if (NativeCoreBridge.isNativeLoaded) {
                val nativePeaks = NativeCoreBridge.extractWaveformPeaksNative(
                    pcmSamples = combinedPcm,
                    targetBars = safeBars
                )
                if (nativePeaks.isNotEmpty()) {
                    return@withContext nativePeaks.toList()
                }
            }

            // Fallback Kotlin
            buildBucketPeaks(combinedPcm, safeBars)
        } catch (t: Throwable) {
            ExportDebugLogger.log(TAG, "Gagal extract waveform: ${t.message}")
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

    private fun buildBucketPeaks(pcm: ShortArray, safeBars: Int): List<Float> {
        if (pcm.isEmpty() || safeBars <= 0) return emptyList()

        val bucketPeaks = FloatArray(safeBars)
        // Jika sampel kurang daripada bilangan bar, setiap bar ambil 1 sampel.
        val step = (pcm.size / safeBars).coerceAtLeast(1)

        for (b in 0 until safeBars) {
            val start = b * step
            if (start >= pcm.size) break
            val end = ((b + 1) * step).coerceAtMost(pcm.size)
            var localMax = 0f
            for (i in start until end) {
                val amp = abs(pcm[i].toInt()) / 32768f
                localMax = max(localMax, amp)
            }
            bucketPeaks[b] = localMax
        }

        val globalMax = bucketPeaks.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
        return bucketPeaks.map { (it / globalMax).coerceIn(0.12f, 1.0f) }
    }
}
