package com.musicsquare.player.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lossless PCM editing for standard RIFF/WAVE 16-bit little-endian input.
 * Trims, removes silent spans and applies speed changes by linear interpolation.
 * Speed resampling changes pitch as well as duration; no pitch-preservation is claimed.
 * Output is a NEW WAV file selected with ACTION_CREATE_DOCUMENT.
 */
data class WavEdit(
 val startMs:Long=0,
 val endMs:Long?=null,
 val removeSilence:Boolean=false,
 val minSilenceMs:Int=1500,
 val thresholdDb:Double=-42.0,
 val speed:Double=1.0
)
data class WavInfo(val channels:Int,val sampleRate:Int,val frames:Int,val pcm:ShortArray)

class WavEditor(private val context:Context) {
 suspend fun edit(source:Uri,destination:Uri,options:WavEdit):Result<Unit> = withContext(Dispatchers.IO) {
  runCatching {
   require(options.speed in 0.25..4.0) { "Speed must be 0.25–4×" }
   require(options.minSilenceMs in 250..30000) { "Silence interval out of range" }
   val wav=context.contentResolver.openInputStream(source)?.use { readWav(it) }
    ?: error("Source cannot be opened")
   val start=((options.startMs*wav.sampleRate)/1000).toInt().coerceIn(0,wav.frames)
   val end=((options.endMs?.times(wav.sampleRate)?.div(1000))?.toInt() ?: wav.frames).coerceIn(start,wav.frames)
   val clipped=wav.pcm.copyOfRange(start*wav.channels,end*wav.channels)
   val filtered=if(options.removeSilence) removeSilences(clipped,wav.channels,wav.sampleRate,options.minSilenceMs,options.thresholdDb) else clipped
   val output=if(options.speed==1.0)filtered else resample(filtered,wav.channels,options.speed)
   context.contentResolver.openOutputStream(destination,"wt")?.use { writeWav(it,wav.channels,wav.sampleRate,output) }
    ?: error("Destination cannot be opened for writing")
  }
 }

 private fun readWav(stream:InputStream):WavInfo {
  val input=DataInputStream(BufferedInputStream(stream))
  fun le16()=java.lang.Short.reverseBytes(input.readShort()).toInt() and 0xffff
  fun le32()=Integer.reverseBytes(input.readInt())
  val riff=ByteArray(4);input.readFully(riff)
  require(String(riff,Charsets.US_ASCII)=="RIFF") { "Only RIFF WAV is supported" }
  le32()
  input.readFully(riff)
  require(String(riff,Charsets.US_ASCII)=="WAVE") { "Invalid WAV header" }
  var channels=0;var sampleRate=0;var bits=0;var encoding=0
  var samples:ShortArray?=null
  while(true) {
   val chunk=ByteArray(4)
   try { input.readFully(chunk) } catch(_:EOFException) { break }
   val size=le32()
   require(size>=0 && size<=256*1024*1024) { "Unsupported chunk size" }
   when(String(chunk,Charsets.US_ASCII)) {
    "fmt " -> {
     require(size>=16)
     encoding=le16();channels=le16();sampleRate=le32()
     le32();le16();bits=le16()
     skipFully(input,size-16)
    }
    "data" -> {
     require(encoding==1 && bits==16 && channels in 1..2 && sampleRate in 8000..192000) {
      "Only 16-bit mono/stereo PCM WAV supported"
     }
     require(size%2==0)
     val data=ShortArray(size/2)
     for(i in data.indices) data[i]=java.lang.Short.reverseBytes(input.readShort())
     samples=data
    }
    else -> skipFully(input,size)
   }
   if(size%2!=0)skipFully(input,1)
   if(samples!=null)break
  }
  val pcm=samples ?: error("WAV audio data missing")
  require(pcm.size%channels==0)
  return WavInfo(channels,sampleRate,pcm.size/channels,pcm)
 }
 private fun skipFully(input:DataInputStream,bytes:Int) {
  var left=bytes
  while(left>0) { val n=input.skipBytes(left);if(n<=0)throw EOFException();left-=n }
 }
 private fun removeSilences(data:ShortArray,channels:Int,rate:Int,minMs:Int,thresholdDb:Double):ShortArray {
  val frames=data.size/channels
  val threshold=(32768.0*Math.pow(10.0,thresholdDb/20.0)).toInt().coerceIn(0,32767)
  val window=max(1,rate/100) // 10ms
  val minWindows=max(1,minMs/10)
  val windows=(frames+window-1)/window
  val silent=BooleanArray(windows)
  for(w in 0 until windows) {
   var peak=0
   for(frame in w*window until min(frames,(w+1)*window)) {
    for(c in 0 until channels)peak=max(peak,abs(data[frame*channels+c].toInt()))
   }
   silent[w]=peak<threshold
  }
  val remove=BooleanArray(windows)
  var i=0
  while(i<windows) {
   if(!silent[i]) { i++;continue }
   var j=i+1
   while(j<windows && silent[j])j++
   if(j-i>=minWindows)for(k in i until j)remove[k]=true
   i=j
  }
  val output=ShortArray(data.size)
  var written=0
  for(frame in 0 until frames) {
   if(remove[frame/window])continue
   for(c in 0 until channels)output[written++]=data[frame*channels+c]
  }
  return output.copyOf(written)
 }
 private fun resample(data:ShortArray,channels:Int,speed:Double):ShortArray {
  val frames=data.size/channels
  if(frames==0)return data
  val outputFrames=(frames/speed).toInt().coerceAtLeast(1)
  require(outputFrames.toLong()*channels<100_000_000L) { "Output too large" }
  val output=ShortArray(outputFrames*channels)
  for(i in 0 until outputFrames) {
   val at=i*speed
   val a=at.toInt().coerceIn(0,frames-1)
   val b=min(frames-1,a+1)
   val mix=at-a
   for(c in 0 until channels) {
    val value=data[a*channels+c]*(1.0-mix)+data[b*channels+c]*mix
    output[i*channels+c]=value.toInt().coerceIn(-32768,32767).toShort()
   }
  }
  return output
 }
 private fun writeWav(stream:OutputStream,channels:Int,rate:Int,data:ShortArray) {
  val output=DataOutputStream(BufferedOutputStream(stream))
  fun le16(n:Int)=output.writeShort(java.lang.Short.reverseBytes(n.toShort()).toInt())
  fun le32(n:Int)=output.writeInt(Integer.reverseBytes(n))
  val bytes=data.size*2
  output.writeBytes("RIFF");le32(36+bytes);output.writeBytes("WAVE")
  output.writeBytes("fmt ");le32(16);le16(1);le16(channels);le32(rate)
  le32(rate*channels*2);le16(channels*2);le16(16)
  output.writeBytes("data");le32(bytes)
  data.forEach { le16(it.toInt()) }
  output.flush()
 }
}
