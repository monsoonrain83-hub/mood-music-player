package com.musicsquare.player.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Bounded PCM WAV peak envelope for the editor. Unsupported WAV encodings
 * return null rather than drawing a misleading waveform.
 */
class WavWaveform(private val context:Context) {
    data class Peaks(val amplitudes:List<Float>,val durationSeconds:Double)

    suspend fun load(uri:Uri,buckets:Int=160):Peaks?=withContext(Dispatchers.IO) {
        require(buckets in 32..512)
        val activeContext=coroutineContext
        context.contentResolver.openInputStream(uri)?.buffered()?.use { stream ->
            fun readExact(size:Int):ByteArray? {
                val bytes=ByteArray(size)
                var offset=0
                while(offset<size) {
                    activeContext.ensureActive()
                    val n=stream.read(bytes,offset,size-offset)
                    if(n<0)return null
                    if(n==0)continue
                    offset+=n
                }
                return bytes
            }
            fun u16(a:ByteArray,p:Int)=((a[p].toInt() and 255) or ((a[p+1].toInt() and 255) shl 8))
            fun u32(a:ByteArray,p:Int)=((a[p].toLong() and 255L) or ((a[p+1].toLong() and 255L) shl 8) or ((a[p+2].toLong() and 255L) shl 16) or ((a[p+3].toLong() and 255L) shl 24))
            fun skipFully(bytes:Long):Boolean {
                var left=bytes
                val buffer=ByteArray(8192)
                while(left>0) {
                    coroutineContext.ensureActive()
                    val n=stream.read(buffer,0,minOf(left,buffer.size.toLong()).toInt())
                    if(n<=0)return false
                    left-=n
                }
                return true
            }
            val header=readExact(12) ?: return@use null
            if(String(header,0,4,Charsets.US_ASCII)!="RIFF" || String(header,8,4,Charsets.US_ASCII)!="WAVE")return@use null
            var channels=0
            var sampleRate=0L
            var blockAlign=0
            var bits=0
            var pcm=false
            var scanned=12L
            while(scanned<1024*1024L) {
                val chunk=readExact(8) ?: return@use null
                val name=String(chunk,0,4,Charsets.US_ASCII)
                val size=u32(chunk,4)
                scanned+=8
                if(size>1024L*1024L*1024L*4L)return@use null
                if(name=="fmt ") {
                    if(size<16 || size>65536)return@use null
                    val format=readExact(size.toInt()) ?: return@use null
                    pcm=u16(format,0)==1
                    channels=u16(format,2)
                    sampleRate=u32(format,4)
                    blockAlign=u16(format,12)
                    bits=u16(format,14)
                } else if(name=="data") {
                    if(!pcm || channels !in 1..8 || sampleRate !in 8000L..384000L || bits!=16 || blockAlign!=channels*2 || size<blockAlign)return@use null
                    val frames=size/blockAlign
                    val values=FloatArray(buckets)
                    val bytes=ByteArray(blockAlign*1024)
                    var readFrames=0L
                    while(readFrames<frames) {
                        coroutineContext.ensureActive()
                        val count=minOf(1024L,frames-readFrames).toInt()
                        val needed=count*blockAlign
                        var offset=0
                        while(offset<needed) {
                            val n=stream.read(bytes,offset,needed-offset)
                            if(n<=0)return@use null
                            offset+=n
                        }
                        for(frame in 0 until count) {
                            val index=(((readFrames+frame)*buckets)/frames).toInt().coerceIn(0,buckets-1)
                            for(ch in 0 until channels) {
                                val p=frame*blockAlign+ch*2
                                val raw=(bytes[p].toInt() and 255) or (bytes[p+1].toInt() shl 8)
                                val sample=raw.toShort().toInt()
                                values[index]=max(values[index],abs(sample.toFloat()/32768f))
                            }
                        }
                        readFrames+=count
                    }
                    return@use Peaks(values.toList(),frames.toDouble()/sampleRate)
                } else {
                    if(!skipFully(size))return@use null
                }
                if(size%2L==1L && !skipFully(1))return@use null
                scanned+=size+(size%2)
            }
            null
        }
    }
}
