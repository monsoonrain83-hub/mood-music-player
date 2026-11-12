package com.musicsquare.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Portable app preferences. Does not copy audio or licensed lyrics. */
class LibraryBackup(private val context:Context) {
 private val names=listOf("library_extras","mood_assignments","favorites","analysis_cache","equalizer","playback_queue")
 fun export():String {
  val root=JSONObject().put("schema",1)
  val entries=JSONObject()
  for(name in names) {
   val source=context.getSharedPreferences(name,Context.MODE_PRIVATE)
   val data=JSONObject()
   source.all.forEach { (key,value) ->
    when(value) {
     is String,is Int,is Long,is Float,is Boolean -> data.put(key,value)
     is Set<*> -> data.put(key,JSONArray(value.filterIsInstance<String>()))
    }
   }
   entries.put(name,data)
  }
  return root.put("preferences",entries).toString(2)
 }
 fun import(json:String):Int {
  require(json.length<=5_000_000) { "Backup too large" }
  val root=JSONObject(json)
  require(root.optInt("schema")==1) { "Unsupported backup version" }
  val entries=root.getJSONObject("preferences")
  var count=0
  for(name in names) {
   val data=entries.optJSONObject(name) ?: continue
   val editor=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit()
   val keys=data.keys()
   while(keys.hasNext()) {
    val key=keys.next()
    when(val value=data.get(key)) {
     is String -> editor.putString(key,value)
     is Boolean -> editor.putBoolean(key,value)
     is Int -> editor.putInt(key,value)
     is Long -> editor.putLong(key,value)
     is Double -> editor.putFloat(key,value.toFloat())
     is JSONArray -> editor.putStringSet(key,(0 until value.length()).map { value.getString(it) }.toSet())
     else -> continue
    }
    count++
   }
   editor.apply()
  }
  return count
 }
}
