package com.musicsquare.player.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
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
            // A provider may support MediaExtractor's URI access while refusing the
            // AssetFileDescriptor API. Fall back only for unsupported/missing assets;
            // permission failures must not be masked.
            val asset = try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")
            } catch (_: java.io.FileNotFoundException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            }
            if (asset != null) {
                asset.use { afd ->
                    if (afd.length >= 0) {
                        try {
                            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        } catch (_: java.io.IOException) {
                            // Some providers expose an asset descriptor but do not support
                            // seeking through it. Retry via the URI before skipping the track.
                            extractor.setDataSource(context, uri, null)
                        } catch (_: IllegalArgumentException) {
                            // A provider can advertise an unusable descriptor range while
                            // still serving the original URI to MediaExtractor.
                            extractor.setDataSource(context, uri, null)
                        }
                    } else {
                        // Unknown-length provider assets may have a nonzero start offset.
                        // Let MediaExtractor resolve the content URI rather than using
                        // the fd-only overload, which would start at byte zero.
                        extractor.setDataSource(context, uri, null)
                    }
                }
            } else {
                // Some providers can serve the URI to MediaExtractor but cannot
                // expose an AssetFileDescriptor. Do not silently skip these tracks.
                extractor.setDataSource(context, uri, null)
            }
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
            var decodedSampleRate = sampleRate
            var decodedChannels = channels
            var windowSamples = (decodedSampleRate / 20).coerceAtLeast(1) // 50 ms
            var windowSum = 0.0
            var windowCount = 0
            var maxSamples = decodedSampleRate.toLong() * 45 * decodedChannels
            var loops = 0
            val deadlineMs = android.os.SystemClock.elapsedRealtime() + 90_000L
            while (!outputEnded && totalSamples < maxSamples && loops++ < 100000) {
                coroutineContext.ensureActive()
                if (android.os.SystemClock.elapsedRealtime() > deadlineMs) return@withContext null
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
                            val bytesPerSample = when (encoding) {
                                android.media.AudioFormat.ENCODING_PCM_16BIT -> 2
                                android.media.AudioFormat.ENCODING_PCM_FLOAT -> 4
                                android.media.AudioFormat.ENCODING_PCM_8BIT -> 1
                                android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                                android.media.AudioFormat.ENCODING_PCM_32BIT -> 4
                                else -> 0
                            }
                            // Unsupported decoder output cannot yield usable acoustic features.
                            // Fail this track immediately rather than spinning until the 90-second
                            // watchdog and delaying classification of the rest of the library.
                            if (bytesPerSample == 0) return@withContext null
                            if (bytesPerSample > 0) {
                                while (pcm.remaining() >= bytesPerSample && totalSamples < maxSamples) {
                                    val sample = when (encoding) {
                                        android.media.AudioFormat.ENCODING_PCM_FLOAT -> pcm.float.toDouble()
                                        android.media.AudioFormat.ENCODING_PCM_8BIT -> (pcm.get().toInt() and 0xff).minus(128) / 128.0
                                        android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                                            // Sign-extend little-endian signed 24-bit PCM.
                                            val low = pcm.get().toInt() and 0xff
                                            val mid = pcm.get().toInt() and 0xff
                                            val high = pcm.get().toInt()
                                            ((high shl 16) or (mid shl 8) or low) / 8388608.0
                                        }
                                        android.media.AudioFormat.ENCODING_PCM_32BIT -> pcm.int.toDouble() / 2147483648.0
                                        else -> pcm.short.toDouble() / 32768.0
                                    }
                                    if (!sample.isFinite()) continue
                                    val sq = sample.coerceIn(-1.0, 1.0).let { it * it }
                                    totalSquared += sq
                                    windowSum += sq
                                    totalSamples++
                                    windowCount++
                                    if (windowCount >= windowSamples * decodedChannels) {
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
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // The decoder can resample or change channel count. Keep the
                        // 50-ms envelope and three-second minimum tied to decoded PCM.
                        val decodedFormat = decoder.outputFormat
                        decodedSampleRate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        decodedChannels = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        if (decodedSampleRate <= 0) return@withContext null
                        windowSamples = (decodedSampleRate / 20).coerceAtLeast(1)
                        maxSamples = decodedSampleRate.toLong() * 45 * decodedChannels
                        // Samples decoded under the previous PCM format cannot be
                        // combined with this format's channel count/sample rate.
                        // Reset the RMS accumulator as well as the beat envelope;
                        // otherwise the three-second minimum and loudness can be
                        // calculated from incompatible audio formats.
                        totalSamples = 0L
                        totalSquared = 0.0
                        envelope.clear()
                        windowSum = 0.0
                        windowCount = 0
                    }
                }
            }
            if (totalSamples < decodedSampleRate.toLong() * decodedChannels * 3) return@withContext null
            val rms = sqrt(totalSquared / totalSamples).toFloat()
            // Energy is a loudness-derived proxy, NOT Spotify's learned energy score.
            val energy = (rms / 0.24f).coerceIn(0f, 1f)
            val bpm = estimateBpm(envelope)
            AudioAnalysis(energy, bpm.first, bpm.second)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            runCatching { extractor.release() }
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
