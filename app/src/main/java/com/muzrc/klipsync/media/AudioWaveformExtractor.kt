package com.muzrc.klipsync.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioWaveformExtractor {

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
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bucketPeaks = FloatArray(targetBars.coerceAtLeast(16))
            val bufferInfo = MediaCodec.BufferInfo()
            var isInputDone = false
            var isOutputDone = false
            var totalSamplesRead = 0L

            val skipStepBytes = if (durationUs > 60_000_000L) 32 else 16

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
                                if (durationUs > 30_000_000L) {
                                    extractor.seekTo(
                                        sampleTimeUs + 180_000L,
                                        MediaExtractor.SEEK_TO_NEXT_SYNC
                                    )
                                } else {
                                    extractor.advance()
                                }
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

                        val bucketIndex = if (durationUs > 0L) {
                            ((bufferInfo.presentationTimeUs.toDouble() / durationUs.toDouble()) * bucketPeaks.size)
                                .toInt()
                                .coerceIn(0, bucketPeaks.lastIndex)
                        } else {
                            ((totalSamplesRead / 1024L) % bucketPeaks.size).toInt()
                        }

                        var localMax = 0f
                        var idx = 0
                        while (idx < remainingShorts) {
                            val amp = abs(shortBuffer.get(idx).toInt()) / 32768f
                            if (amp > localMax) localMax = amp
                            idx += skipStepBytes
                        }

                        bucketPeaks[bucketIndex] = max(bucketPeaks[bucketIndex], localMax)
                        totalSamplesRead += remainingShorts
                    }
                    codec.releaseOutputBuffer(outIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputDone = true
                    }
                }
            }

            val globalMax = bucketPeaks.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
            bucketPeaks.map { peak ->
                (peak / globalMax).coerceIn(0.12f, 1.0f)
            }
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
