package com.musicsquare.player.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Android MediaCodec audio waveform for supported compressed formats.
 * Decodes into bounded peak buckets, never accumulates decoded PCM in memory.
 */
class DecodedWaveform(private val context:Context) {
    suspend fun load(uri:Uri,buckets:Int=160):WavWaveform.Peaks?=withContext(Dispatchers.IO) {
        require(buckets in 32..512)
        val extractor=MediaExtractor()
        var codec:MediaCodec?=null
        try {
            extractor.setDataSource(context,uri,null)
            val track=(0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true
            } ?: return@withContext null
            val format=extractor.getTrackFormat(track)
            val mime=format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val durationUs=if(format.containsKey(MediaFormat.KEY_DURATION))format.getLong(MediaFormat.KEY_DURATION) else 0L
            if(durationUs<=0L)return@withContext null
            extractor.selectTrack(track)
            val decoder=MediaCodec.createDecoderByType(mime)
            codec=decoder
            decoder.configure(format,null,null,0)
            decoder.start()
            val peaks=FloatArray(buckets)
            val info=MediaCodec.BufferInfo()
            var inputDone=false
            var outputDone=false
            var sampleRate=if(format.containsKey(MediaFormat.KEY_SAMPLE_RATE))format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var channels=if(format.containsKey(MediaFormat.KEY_CHANNEL_COUNT))format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            var processedFrames=0L
            val totalFrames=max(1L,durationUs*sampleRate/1_000_000L)
            var idle=0
            while(!outputDone) {
                coroutineContext.ensureActive()
                if(!inputDone) {
                    val index=decoder.dequeueInputBuffer(10_000)
                    if(index>=0) {
                        val buffer=decoder.getInputBuffer(index) ?: error("Missing input buffer")
                        val count=extractor.readSampleData(buffer,0)
                        if(count<0) {
                            decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone=true
                        } else {
                            decoder.queueInputBuffer(index,0,count,extractor.sampleTime,0)
                            extractor.advance()
                        }
                    }
                }
                val index=decoder.dequeueOutputBuffer(info,10_000)
                if(index>=0) {
                    idle=0
                    val buffer=decoder.getOutputBuffer(index)
                    if(buffer!=null && info.size>0) {
                        val sampleFormat=decoder.outputFormat
                        sampleRate=sampleFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels=sampleFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val encoding=if(sampleFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            sampleFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else 2
                        val bytesPerSample=if(encoding==4)4 else 2
                        val frameSize=channels*bytesPerSample
                        if(channels !in 1..8 || frameSize<=0 || encoding !in listOf(2,4))return@withContext null
                        val data=buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        data.position(info.offset)
                        data.limit(info.offset+info.size)
                        val frames=info.size/frameSize
                        for(frame in 0 until frames) {
                            val bucket=((processedFrames+frame)*buckets/totalFrames).toInt().coerceIn(0,buckets-1)
                            for(ch in 0 until channels) {
                                val value=if(encoding==4)abs(data.float) else abs(data.short.toFloat()/32768f)
                                if(value.isFinite())peaks[bucket]=max(peaks[bucket],value.coerceIn(0f,1f))
                            }
                        }
                        processedFrames+=frames
                    }
                    outputDone=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                    decoder.releaseOutputBuffer(index,false)
                } else if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out=decoder.outputFormat
                    sampleRate=out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels=out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else {
                    idle++
                    if(idle>1000)error("Audio decoder stalled")
                }
            }
            WavWaveform.Peaks(peaks.toList(),durationUs/1_000_000.0)
        } catch(e:kotlinx.coroutines.CancellationException) {
            throw e
        } catch(_:Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
