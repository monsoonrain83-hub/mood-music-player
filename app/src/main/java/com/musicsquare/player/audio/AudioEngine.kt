package com.musicsquare.player.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Offline audio processing using maintained FFmpegKit.
 * All output goes to a newly selected SAF document, never overwriting the input.
 */
class AudioEngine(private val context:Context) {
 private fun sameDocument(a:Uri,b:Uri):Boolean {
  if(a==b) return true
  if(a.scheme=="content" && b.scheme=="content" && a.authority==b.authority &&
   DocumentsContract.isDocumentUri(context,a) && DocumentsContract.isDocumentUri(context,b)) {
   return runCatching {
    DocumentsContract.getDocumentId(a)==DocumentsContract.getDocumentId(b)
   }.getOrDefault(false)
  }
  return false
 }
 private fun digest(file:File):ByteArray {
  val hash=MessageDigest.getInstance("SHA-256")
  file.inputStream().use { stream ->
   val buffer=ByteArray(64*1024)
   while(true) {
    val count=stream.read(buffer)
    if(count<0)break
    if(count>0)hash.update(buffer,0,count)
   }
  }
  return hash.digest()
 }

 /** SAF document overwrites are not atomic; preserve a rollback copy. */
 private fun replaceAndVerify(uri:Uri,newFile:File,original:File) {
  fun overwrite(file:File) {
   val pfd=context.contentResolver.openFileDescriptor(uri,"rwt")
    ?: error("Provider does not support truncate/write")
   pfd.use { descriptor ->
    file.inputStream().use { input ->
     FileOutputStream(descriptor.fileDescriptor).use { output ->
      input.copyTo(output)
      output.flush()
      output.fd.sync()
     }
    }
   }
  }
  val expected=digest(newFile)
  try {
   overwrite(newFile)
   val actual=MessageDigest.getInstance("SHA-256")
   var count=0L
   context.contentResolver.openInputStream(uri)?.use { input ->
    val chunk=ByteArray(64*1024)
    while(true) {
     val n=input.read(chunk)
     if(n<0)break
     if(n>0) { actual.update(chunk,0,n); count+=n }
    }
   } ?: error("Cannot read modified document")
   check(count==newFile.length() && expected.contentEquals(actual.digest())) {
    "Saved document differs from generated file"
   }
  } catch(writeFailure:Exception) {
   val restoreFailure=runCatching { overwrite(original) }.exceptionOrNull()
   if(restoreFailure!=null)
    error("Metadata write failed AND restoration failed: ${writeFailure.message}; ${restoreFailure.message}")
   error("Metadata write failed; restored original: ${writeFailure.message}")
  }
 }

 enum class Format(val ext:String,val mime:String,val codec:String) {
  MP3("mp3","audio/mpeg","libmp3lame"),
  WAV("wav","audio/wav","pcm_s16le"),
  FLAC("flac","audio/flac","flac"),
  OGG("ogg","audio/ogg","libvorbis"),
  M4A("m4a","audio/mp4","aac")
 }
 data class Edit(
  val startSeconds:Double=0.0,
  val endSeconds:Double?=null,
  val removeRanges:List<Pair<Double,Double>> = emptyList(),
  val silenceSeconds:Double?=null,
  val speed:Double=1.0,
  val preservePitch:Boolean=true,
  val fadeInSeconds:Double=0.0,
  val fadeOutSeconds:Double=0.0,
  val normalizeLoudness:Boolean=false
 )
 data class Metadata(
  val title:String?=null,val artist:String?=null,val album:String?=null,
  val year:String?=null,val genre:String?=null,val track:String?=null,
  val comment:String?=null
 )
 suspend fun convert(source:Uri,destination:Uri,format:Format,bitrate:Int=192,edit:Edit=Edit()):Result<Unit> =
  process(source,destination,format,bitrate,edit,null)
 suspend fun writeTags(source:Uri,destination:Uri,format:Format,metadata:Metadata):Result<Unit> =
  process(source,destination,format,192,Edit(),metadata)

 private suspend fun process(source:Uri,destination:Uri,format:Format,bitrate:Int,edit:Edit,tags:Metadata?):Result<Unit> =
  withContext(Dispatchers.IO) {
   try {
    coroutineContext.ensureActive()
    require(tags!=null || !sameDocument(source,destination)) { "Conversion requires a different destination; original file is protected" }
    require(bitrate in listOf(64,96,128,160,192,256,320))
    require(edit.speed.isFinite() && edit.speed in 0.25..4.0)
    require(edit.startSeconds.isFinite() && edit.startSeconds>=0)
    require(edit.endSeconds==null || (edit.endSeconds.isFinite() && edit.endSeconds>edit.startSeconds))
    require(edit.fadeInSeconds.isFinite() && edit.fadeInSeconds in 0.0..30.0)
    require(edit.fadeOutSeconds.isFinite() && edit.fadeOutSeconds in 0.0..30.0)
    require(edit.fadeOutSeconds==0.0 || (edit.endSeconds!=null && edit.endSeconds-edit.startSeconds>edit.fadeOutSeconds && edit.removeRanges.isEmpty() && edit.silenceSeconds==null)) { "Fade-out needs an end time and cannot combine with cut ranges or silence removal" }
    require(edit.removeRanges.size<=32)
    require(edit.removeRanges.all { it.first.isFinite() && it.second.isFinite() && it.first>=0 && it.second>it.first })
    require(edit.removeRanges.all { edit.endSeconds==null || it.first<edit.endSeconds })
    require(edit.silenceSeconds==null || (edit.silenceSeconds.isFinite() && edit.silenceSeconds in 0.25..30.0))
    val jobContext=coroutineContext
    val input=File.createTempFile("audio_input_",".tmp",context.cacheDir)
    val output=File.createTempFile("audio_output_",".${format.ext}",context.cacheDir)
    try {
     coroutineContext.ensureActive()
     context.contentResolver.openInputStream(source)?.use { stream ->
      input.outputStream().use { target ->
       AudioFileSafety.copyBounded(stream,target) { jobContext.ensureActive() }
      }
     } ?: error("Cannot open source")
     val command=mutableListOf("-hide_banner","-loglevel","error","-y","-i",input.absolutePath)
     val filters=mutableListOf<String>()
     if(edit.startSeconds>0 || edit.endSeconds!=null) {
      val args=mutableListOf("start=${edit.startSeconds}")
      edit.endSeconds?.let { args+="end=$it" }
      filters+="atrim=${args.joinToString(":")}"
      filters+="asetpts=PTS-STARTPTS"
     }
     if(edit.removeRanges.isNotEmpty()) {
      val keep=edit.removeRanges.filter { it.second>edit.startSeconds }
       .sortedBy { it.first }
      // select expression removes arbitrary intervals in original timeline.
      // Apply before atrim; replace the previous trimming filters.
      filters.clear()
      val excludes=keep.joinToString("+") { "between(t\\,${it.first}\\,${it.second})" }
      if(excludes.isNotEmpty())filters+="aselect=not($excludes)"
      if(edit.startSeconds>0 || edit.endSeconds!=null) {
       val cond=mutableListOf<String>()
       if(edit.startSeconds>0)cond+="gte(t\\,${edit.startSeconds})"
       edit.endSeconds?.let { cond+="lt(t\\,$it)" }
       filters+="aselect=${cond.joinToString("*")}"
      }
      filters+="asetpts=N/SR/TB"
     }
     edit.silenceSeconds?.let { sec ->
      require(sec in 0.25..30.0)
      // silenceremove removes long silent sections, including internal spans.
      filters+="silenceremove=start_periods=0:stop_periods=-1:stop_duration=$sec:stop_threshold=-42dB:stop_silence=0.05"
     }
     if(edit.fadeInSeconds>0.0)filters+="afade=t=in:st=0:d=${edit.fadeInSeconds}"
     if(edit.fadeOutSeconds>0.0) {
      // This is relative to the trimmed output, before tempo adjustment.
      val end=edit.endSeconds!!-edit.startSeconds
      filters+="afade=t=out:st=${end-edit.fadeOutSeconds}:d=${edit.fadeOutSeconds}"
     }
     if(edit.speed!=1.0) {
      if(edit.preservePitch) {
       var remaining=edit.speed
       while(remaining>2.0) { filters+="atempo=2.0";remaining/=2.0 }
       while(remaining<0.5) { filters+="atempo=0.5";remaining/=0.5 }
       filters+="atempo=$remaining"
      } else filters+="asetrate=44100*${edit.speed},aresample=44100"
     }
     if(edit.normalizeLoudness)filters+="loudnorm=I=-16:TP=-1.5:LRA=11"
     if(filters.isNotEmpty())command+=listOf("-af",filters.joinToString(","))
     if(tags!=null) {
      listOf("title" to tags.title,"artist" to tags.artist,"album" to tags.album,
       "date" to tags.year,"genre" to tags.genre,"track" to tags.track,
       "comment" to tags.comment).forEach { (key,value) ->
        if(value!=null)command+=listOf("-metadata","$key=$value")
       }
     }
     if(tags!=null)command+=listOf("-map","0","-c","copy")
     else {
      command+=listOf("-c:a",format.codec)
      if(format in listOf(Format.MP3,Format.M4A,Format.OGG))
       command+=listOf("-b:a","${bitrate}k")
     }
     if(tags==null)command+="-vn"
     command+=output.absolutePath
     // FFmpegKit argument API avoids shell escaping and injection.
     val session=CancellableFfmpeg.execute(command.toTypedArray())
     coroutineContext.ensureActive()
     require(ReturnCode.isSuccess(session.returnCode)) {
      "FFmpeg error: ${session.allLogsAsString.takeLast(600)}"
     }
     require(output.length()>0) { "Empty output" }
     require(output.length()<=AudioFileSafety.MAX_OUTPUT_BYTES) { "Converted output exceeds size limit" }
     coroutineContext.ensureActive()
     if(tags!=null && sameDocument(source,destination)) {
      replaceAndVerify(destination,output,input)
     } else {
      context.contentResolver.openOutputStream(destination,"wt")?.use { stream ->
       output.inputStream().use { inputStream ->
        AudioFileSafety.copyBounded(inputStream,stream,AudioFileSafety.MAX_OUTPUT_BYTES) {
         jobContext.ensureActive()
        }
       }
       stream.flush()
      } ?: error("Cannot write destination")
      // Stream the provider read-back directly into a digest: no second
      // temporary copy of potentially multi-gigabyte converted audio.
      val expected=digest(output)
      val (bytesRead,actual)=context.contentResolver.openInputStream(destination)?.use { stream ->
       AudioFileSafety.sha256(stream) { jobContext.ensureActive() }
      } ?: error("Cannot verify output document")
      check(bytesRead==output.length() && expected.contentEquals(actual)) {
       "Output verification failed; destination may be incomplete"
      }
     }
    } finally { input.delete();output.delete() }
    Result.success(Unit)
   } catch(cancelled:CancellationException) {
    throw cancelled
   } catch(error:Exception) {
    Result.failure(error)
   }
  }
}
