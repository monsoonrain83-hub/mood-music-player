package com.musicsquare.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LibraryExtras(context: Context) {
 private val prefs=context.getSharedPreferences("library_extras",Context.MODE_PRIVATE)
 fun plays(id:Long)=prefs.getInt("plays_$id",0)
 fun played(id:Long)=prefs.edit().putInt("plays_$id",plays(id)+1).apply()
 fun playlistNames():List<String> = prefs.getString("playlist_names","[]")?.let { raw ->
  runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
 } ?: emptyList()
 fun createPlaylist(name:String) {
  val n=name.trim()
  if(n.isBlank() || playlistNames().contains(n))return
  prefs.edit().putString("playlist_names",JSONArray(playlistNames()+n).toString()).apply()
 }
 fun playlist(name:String):Set<Long> {
  val raw=prefs.getString("playlist_$name","[]") ?: "[]"
  return runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() } }.getOrDefault(emptySet())
 }
 fun add(name:String,id:Long) {
  prefs.edit().putString("playlist_$name",JSONArray((playlist(name)+id).toList()).toString()).apply()
 }
 fun remove(name:String,id:Long) {
  prefs.edit().putString("playlist_$name",JSONArray((playlist(name)-id).toList()).toString()).apply()
 }
}
