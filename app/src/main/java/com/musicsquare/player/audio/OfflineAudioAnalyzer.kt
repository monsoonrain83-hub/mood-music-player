package com.musicsquare.player.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Decodes up to 45 seconds locally with Android MediaCodec.
 * Estimates loudness/energy and beat periodicity. It does NOT infer valence:
 * happy vs sad needs a separately validated ML classifier or manual annotation.
 */
data class AudioAnalysis(
    val energy: Float,
    val bpm: Float?,
    val confidence: Float,
    val valence: Float? = null
)

class OfflineAudioAnalyzer(private val context: Context) {
    suspend fun analyze(uri: Uri): AudioAnalysis? = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length >= 0) extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                else extractor.setDataSource(afd.fileDescriptor)
            } ?: return@withContext null
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            if (sampleRate <= 0) return@withContext null
            codec = MediaCodec.createDecoderByType(mime)
            val decoder = codec!!
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var totalSquared = 0.0
            var totalSamples = 0L
            val envelope = ArrayList<Double>()
            val windowSamples = (sampleRate / 20).coerceAtLeast(1) // 50 ms
            var windowSum = 0.0
            var windowCount = 0
            val maxSamples = sampleRate.toLong() * 45 * channels
            var loops = 0
            while (!outputEnded && totalSamples < maxSamples && loops++ < 100000) {
                if (!inputEnded) {
                    val index = decoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val bytes = extractor.readSampleData(buffer, 0)
                        if (bytes < 0 || extractor.sampleTime > 45_000_000L) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(index, 0, bytes, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10000)
                when {
                    index >= 0 -> {
                        val output = decoder.getOutputBuffer(index)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val pcm = output.slice().order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            // Most Android PCM decoders emit signed 16-bit audio.
                            val outputFormat = decoder.outputFormat
                            val encoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else android.media.AudioFormat.ENCODING_PCM_16BIT
                            if (encoding == android.media.AudioFormat.ENCODING_PCM_16BIT) {
                                while (pcm.remaining() >= 2 && totalSamples < maxSamples) {
                                    val sample = pcm.short.toDouble() / 32768.0
                                    val sq = sample * sample
                                    totalSquared += sq
                                    windowSum += sq
                                    totalSamples++
                                    windowCount++
                                    if (windowCount >= windowSamples * channels) {
                                        envelope.add(sqrt(windowSum / windowCount))
                                        windowCount = 0
                                        windowSum = 0.0
                                    }
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputEnded = true
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                }
            }
            if (totalSamples < sampleRate.toLong() * channels * 3) return@withContext null
            val rms = sqrt(totalSquared / totalSamples).toFloat()
            // Energy is a loudness-derived proxy, NOT Spotify's learned energy score.
            val energy = (rms / 0.24f).coerceIn(0f, 1f)
            val bpm = estimateBpm(envelope)
            AudioAnalysis(energy, bpm.first, bpm.second)
        } catch (_: Exception) {
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun estimateBpm(envelope: List<Double>): Pair<Float?, Float> {
        if (envelope.size < 160) return null to 0f
        val novelty = DoubleArray(envelope.size) { i ->
            if (i == 0) 0.0 else max(0.0, envelope[i] - envelope[i - 1])
        }
        val mean = novelty.average()
        if (mean < 0.00001) return null to 0f
        val centered = novelty.map { it - mean }
        var bestLag = -1
        var bestScore = 0.0
        var runnerUp = 0.0
        // Envelope rate = 20 Hz, 60–200 BPM.
        for (lag in 6..20) {
            var dot = 0.0
            var a = 0.0
            var b = 0.0
            for (i in lag until centered.size) {
                dot += centered[i] * centered[i-lag]
                a += centered[i] * centered[i]
                b += centered[i-lag] * centered[i-lag]
            }
            val score = if (a*b > 1e-15) dot / sqrt(a*b) else 0.0
            if (score > bestScore) { runnerUp = bestScore; bestScore = score; bestLag = lag }
            else if (score > runnerUp) runnerUp = score
        }
        if (bestLag < 0 || bestScore < 0.12) return null to 0f
        val bpm = 1200f / bestLag
        return bpm to bestScore.toFloat().coerceIn(0f,1f)
    }
}
