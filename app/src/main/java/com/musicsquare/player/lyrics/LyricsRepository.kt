package com.musicsquare.player.lyrics

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

data class TimedLine(val atMs:Long,val text:String)
data class Lyrics(val lines:List<TimedLine>,val plain:String?,val source:String) {
 val synced get()=lines.isNotEmpty()
 fun currentLine(positionMs:Long):String? = lines.lastOrNull { it.atMs<=positionMs }?.text?.takeIf { it.isNotBlank() }
}
object LrcParser {
 private val stamp=Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")
 fun parse(text:String):List<TimedLine> {
  val result=mutableListOf<TimedLine>()
  for(row in text.lineSequence()) {
   val matches=stamp.findAll(row).toList()
   val lyric=stamp.replace(row,"").trim()
   for(m in matches) {
    val min=m.groupValues[1].toLongOrNull() ?: continue
    val sec=m.groupValues[2].toLongOrNull() ?: continue
    if(sec>=60)continue
    val frac=m.groupValues[3].padEnd(3,'0').take(3).toLongOrNull() ?: 0L
    result+=TimedLine((min*60+sec)*1000+frac,lyric)
   }
  }
  return result.sortedBy { it.atMs }
 }
}
class LyricsRepository(private val context:Context) {
 private val prefs=context.getSharedPreferences("lyrics_cache",Context.MODE_PRIVATE)
 private fun key(title:String,artist:String,duration:Long):String {
  val bytes=MessageDigest.getInstance("SHA-256").digest("$title|$artist|$duration".toByteArray())
  return bytes.joinToString("") { "%02x".format(it) }
 }
 fun saved(title:String,artist:String,duration:Long):Lyrics? {
  val raw=prefs.getString(key(title,artist,duration),null) ?: return null
  return decode(raw,"Offline cache")
 }
 fun saveLrc(title:String,artist:String,duration:Long,lrc:String) {
  prefs.edit().putString(key(title,artist,duration),JSONObject().put("syncedLyrics",lrc).toString()).apply()
 }
 fun savePlain(title:String,artist:String,duration:Long,plain:String) {
  prefs.edit().putString(key(title,artist,duration),JSONObject().put("plainLyrics",plain).toString()).apply()
 }
 fun exportLrc(title:String,artist:String,duration:Long):String? {
  val result=saved(title,artist,duration) ?: return null
  if(result.lines.isEmpty())return null
  return result.lines.joinToString("\\n") { line ->
   val min=line.atMs/60000;val sec=(line.atMs/1000)%60;val cent=(line.atMs%1000)/10
   "[%02d:%02d.%02d] %s".format(java.util.Locale.US,min,sec,cent,line.text)
  }
 }
 suspend fun fetch(title:String,artist:String,album:String,durationMs:Long):Lyrics? =
  withContext(Dispatchers.IO) {
   val cacheKey=key(title,artist,durationMs)
   prefs.getString(cacheKey,null)?.let { return@withContext decode(it,"Offline cache") }
   if(title.isBlank() || artist.isBlank())return@withContext null
   val params=listOf("track_name" to title,"artist_name" to artist).toMutableList()
   if(album.isNotBlank())params+="album_name" to album
   if(durationMs in 1000..3_600_000)params+="duration" to (durationMs/1000).toString()
   val query=params.joinToString("&") { (k,v)->"$k=${URLEncoder.encode(v,"UTF-8")}" }
   val connection=(URL("https://lrclib.net/api/get?$query").openConnection() as HttpURLConnection)
   connection.connectTimeout=6000
   connection.readTimeout=6000
   connection.setRequestProperty("User-Agent","MusicSquarePlayer/0.3 (https://github.com/monsoonrain83-hub/mood-music-player)")
   try {
    if(connection.responseCode!=200)return@withContext null
    val raw=connection.inputStream.bufferedReader().use { it.readText() }
    val decoded=decode(raw,"LRCLIB") ?: return@withContext null
    prefs.edit().putString(cacheKey,raw).apply()
    decoded
   } catch(_:Exception) { null }
   finally { connection.disconnect() }
  }
 private fun decode(raw:String,source:String):Lyrics?=runCatching {
  val obj=JSONObject(raw)
  val synced=obj.optString("syncedLyrics").takeUnless { it=="null" || it.isBlank() }
  val plain=obj.optString("plainLyrics").takeUnless { it=="null" || it.isBlank() }
  Lyrics(synced?.let { LrcParser.parse(it) } ?: emptyList(),plain,source)
 }.getOrNull()
}
