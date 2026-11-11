package com.musicsquare.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local queue snapshot, never persists arbitrary file permissions. */
class PlaybackQueueStore(context:Context) {
 private val prefs=context.getSharedPreferences("playback_queue",Context.MODE_PRIVATE)
 data class Snapshot(val ids:List<Long>,val index:Int,val positionMs:Long,val shuffle:Boolean,val repeat:Int)
 fun save(ids:List<Long>,index:Int,positionMs:Long,shuffle:Boolean,repeat:Int) {
  if(ids.isEmpty())return
  val json=JSONObject()
   .put("ids",JSONArray(ids))
   .put("index",index)
   .put("position",positionMs.coerceAtLeast(0L))
   .put("shuffle",shuffle)
   .put("repeat",repeat)
  prefs.edit().putString("queue",json.toString()).apply()
 }
 fun read():Snapshot?=runCatching {
  val obj=JSONObject(prefs.getString("queue",null) ?: return null)
  val arr=obj.getJSONArray("ids")
  val ids=(0 until arr.length()).map { arr.getLong(it) }
  if(ids.isEmpty())null else Snapshot(ids,obj.optInt("index",0).coerceIn(0,ids.lastIndex),obj.optLong("position",0L),obj.optBoolean("shuffle"),obj.optInt("repeat"))
 }.getOrNull()
 fun clear() { prefs.edit().remove("queue").apply() }
}
