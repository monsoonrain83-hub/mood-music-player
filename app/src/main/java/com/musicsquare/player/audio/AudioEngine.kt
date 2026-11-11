package com.musicsquare.player.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline audio processing using maintained FFmpegKit.
 * All output goes to a newly selected SAF document, never overwriting the input.
 */
class AudioEngine(private val context:Context) {
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
  val preservePitch:Boolean=true
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
   runCatching {
    require(bitrate in listOf(64,96,128,160,192,256,320))
    require(edit.speed in 0.25..4.0)
    require(edit.startSeconds>=0)
    require(edit.endSeconds==null || edit.endSeconds>edit.startSeconds)
    require(edit.removeRanges.size<=32)
    val input=File.createTempFile("audio_input_",".tmp",context.cacheDir)
    val output=File.createTempFile("audio_output_",".${format.ext}",context.cacheDir)
    try {
     context.contentResolver.openInputStream(source)?.use { stream ->
      input.outputStream().use { stream.copyTo(it) }
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
      val base=edit.startSeconds
      val keep=edit.removeRanges.filter { it.second>it.first && it.second>base }
       .sortedBy { it.first }
      var cursor=base
      val segments=mutableListOf<Pair<Double,Double?>>()
      for((a,b) in keep) {
       if(a>cursor)segments+=(cursor to a)
       cursor=maxOf(cursor,b)
      }
      segments+=(cursor to edit.endSeconds)
      // select expression removes arbitrary intervals in original timeline.
      // Apply before atrim; replace the previous trimming filters.
      filters.clear()
      val excludes=keep.joinToString("+") { "between(t,${it.first},${it.second})" }
      if(excludes.isNotEmpty())filters+="aselect=not($excludes)"
      if(edit.startSeconds>0 || edit.endSeconds!=null) {
       val cond=mutableListOf<String>()
       if(edit.startSeconds>0)cond+="gte(t,${edit.startSeconds})"
       edit.endSeconds?.let { cond+="lt(t,$it)" }
       filters+="aselect=${cond.joinToString("*")}"
      }
      filters+="asetpts=N/SR/TB"
     }
     edit.silenceSeconds?.let { sec ->
      require(sec in 0.25..30.0)
      // silenceremove removes long silent sections, including internal spans.
      filters+="silenceremove=start_periods=0:stop_periods=-1:stop_duration=$sec:stop_threshold=-42dB:stop_silence=0.05"
     }
     if(edit.speed!=1.0) {
      if(edit.preservePitch) {
       var remaining=edit.speed
       while(remaining>2.0) { filters+="atempo=2.0";remaining/=2.0 }
       while(remaining<0.5) { filters+="atempo=0.5";remaining/=0.5 }
       filters+="atempo=$remaining"
      } else filters+="asetrate=44100*${edit.speed},aresample=44100"
     }
     if(filters.isNotEmpty())command+=listOf("-af",filters.joinToString(","))
     if(tags!=null) {
      listOf("title" to tags.title,"artist" to tags.artist,"album" to tags.album,
       "date" to tags.year,"genre" to tags.genre,"track" to tags.track,
       "comment" to tags.comment).forEach { (key,value) ->
        if(value!=null)command+=listOf("-metadata","$key=$value")
       }
     }
     if(tags!=null)command+=listOf("-c:a","copy")
     else {
      command+=listOf("-c:a",format.codec)
      if(format in listOf(Format.MP3,Format.M4A,Format.OGG))
       command+=listOf("-b:a","${bitrate}k")
     }
     command+=listOf("-vn",output.absolutePath)
     // FFmpegKit argument API avoids shell escaping and injection.
     val session=FFmpegKit.executeWithArguments(command.toTypedArray())
     require(ReturnCode.isSuccess(session.returnCode)) {
      "FFmpeg error: ${session.allLogsAsString.takeLast(600)}"
     }
     require(output.length()>0) { "Empty output" }
     context.contentResolver.openOutputStream(destination,"wt")?.use { stream ->
      output.inputStream().use { it.copyTo(stream) }
     } ?: error("Cannot write destination")
    } finally { input.delete();output.delete() }
    Unit
   }
  }
}
