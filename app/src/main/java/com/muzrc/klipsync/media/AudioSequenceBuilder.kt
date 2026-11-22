package com.muzrc.klipsync.media

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.muzrc.klipsync.model.AudioTrackConfig
import java.nio.ByteBuffer
import kotlin.math.roundToInt

@UnstableApi
object AudioSequenceBuilder {

    fun buildMusicSequence(audioConfig: AudioTrackConfig): EditedMediaItemSequence? {
        val uri = audioConfig.uri ?: return null
        val effectiveDurationMs = audioConfig.effectiveAudioDurationMs
        if (effectiveDurationMs <= 0L) return null

        val startMs = audioConfig.startOffsetMs.coerceAtLeast(0L)
        val endMs = audioConfig.totalDurationMs

        // Validasi: clipping tidak boleh negatif atau sifar
        if (endMs <= startMs) return null

        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clippingConfig)
            .build()

        val volumeProcessor = VolumeFadeAudioProcessor(
            targetVolume = audioConfig.musicVolume.coerceIn(0f, 1.5f),
            totalDurationMs = effectiveDurationMs,
            fadeInMs = audioConfig.fadeInDurationMs.coerceAtLeast(0L),
            fadeOutMs = audioConfig.fadeOutDurationMs.coerceAtLeast(0L)
        )

        val editedAudio = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(listOf(volumeProcessor), emptyList()))
            .build()

        return EditedMediaItemSequence(listOf(editedAudio))
    }

    fun createConstantVolumeProcessor(volume: Float): AudioProcessor {
        return VolumeFadeAudioProcessor(
            targetVolume = volume.coerceIn(0f, 1.5f),
            totalDurationMs = Long.MAX_VALUE,
            fadeInMs = 0L,
            fadeOutMs = 0L
        )
    }

    private class VolumeFadeAudioProcessor(
        private val targetVolume: Float,
        private val totalDurationMs: Long,
        private val fadeInMs: Long,
        private val fadeOutMs: Long
    ) : BaseAudioProcessor() {

        private var processedFrames: Long = 0L

        // Simpan baki byte yang tidak lengkap (frame separuh) untuk diproses
        // bersama buffer seterusnya. Ini mengelak output rosak.
        private var pendingByte: Byte? = null

        override fun onConfigure(
            inputAudioFormat: AudioProcessor.AudioFormat
        ): AudioProcessor.AudioFormat {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
                // Kembalikan NOT_SET supaya chain tidak crash; Transformer
                // akan skip processor ini jika format tidak disokong.
                return AudioProcessor.AudioFormat.NOT_SET
            }
            return inputAudioFormat
        }

        override fun onFlush() {
            processedFrames = 0L
            pendingByte = null
        }

        override fun onReset() {
            processedFrames = 0L
            pendingByte = null
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val remaining = inputBuffer.remaining()
            if (remaining == 0 && pendingByte == null) return

            val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
            val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)
            val bytesPerFrame = channelCount * 2

            // Kira saiz output: baki byte + remaining, dibundarkan ke bawah
            // ke gandaan bytesPerFrame, ditambah baki baru (jika ada).
            val totalAvailable = remaining + (if (pendingByte != null) 1 else 0)
            val completeBytes = (totalAvailable / bytesPerFrame) * bytesPerFrame
            val newPendingCount = totalAvailable - completeBytes

            val outBuffer = replaceOutputBuffer(completeBytes)
            if (completeBytes == 0) {
                // Simpan semua sebagai pending
                pendingByte?.let { outBuffer.put(it) } // tidak sepatutnya berlaku
                // Simpan balik ke pending (hanya 1 byte mungkin)
                if (remaining > 0) {
                    pendingByte = inputBuffer.get()
                }
                outBuffer.flip()
                return
            }

            // Bina buffer kerja sementara jika ada pending byte
            val workBuffer: ByteBuffer
            if (pendingByte != null) {
                workBuffer = ByteBuffer.allocate(totalAvailable)
                workBuffer.put(pendingByte!!)
                pendingByte = null
                val slice = ByteBuffer.allocate(remaining)
                inputBuffer.get(slice.array())
                workBuffer.put(slice.array())
                workBuffer.flip()
            } else {
                workBuffer = inputBuffer
            }

            // Proses frame lengkap
            while (workBuffer.remaining() >= bytesPerFrame) {
                val currentTimeMs = (processedFrames * 1000L) / sampleRate
                val gain = computeGainAt(currentTimeMs)

                for (ch in 0 until channelCount) {
                    val sample = workBuffer.short
                    val scaled = (sample * gain)
                        .roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                    outBuffer.putShort(scaled)
                }
                processedFrames++
            }

            // Simpan baki byte baru (jika ada) untuk buffer seterusnya
            if (workBuffer.hasRemaining()) {
                pendingByte = workBuffer.get()
            }

            outBuffer.flip()
        }

        private fun computeGainAt(timeMs: Long): Float {
            var factor = targetVolume

            if (fadeInMs > 0L && timeMs < fadeInMs) {
                factor *= (timeMs.toFloat() / fadeInMs.toFloat()).coerceIn(0f, 1f)
            }

            if (fadeOutMs > 0L && totalDurationMs != Long.MAX_VALUE) {
                val fadeOutStartMs = (totalDurationMs - fadeOutMs).coerceAtLeast(0L)
                if (timeMs > fadeOutStartMs) {
                    val remaining = (totalDurationMs - timeMs).coerceAtLeast(0L)
                    factor *= (remaining.toFloat() / fadeOutMs.toFloat()).coerceIn(0f, 1f)
                }
            }

            return factor
        }
    }
}
