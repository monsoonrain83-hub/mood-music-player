package com.musicsquare.player.audio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small, bounded on-device history. Stores counts, not source paths or URIs. */
class ConversionHistory(context:Context) {
    private val prefs=context.getSharedPreferences("conversion_history",Context.MODE_PRIVATE)
    data class Entry(val timestamp:Long,val format:String,val completed:Int,val failed:Int)
    fun recent():List<Entry> = runCatching {
        val array=JSONArray(prefs.getString("recent","[]"))
        (0 until array.length()).map { index ->
            val obj=array.getJSONObject(index)
            Entry(obj.getLong("time"),obj.getString("format"),obj.getInt("ok"),obj.getInt("failed"))
        }
    }.getOrDefault(emptyList())
    fun add(format:AudioEngine.Format,completed:Int,failed:Int) {
        val next=listOf(Entry(System.currentTimeMillis(),format.ext,completed,failed))+recent()
        val array=JSONArray()
        next.take(20).forEach { entry ->
            array.put(JSONObject().put("time",entry.timestamp).put("format",entry.format)
                .put("ok",entry.completed).put("failed",entry.failed))
        }
        prefs.edit().putString("recent",array.toString()).apply()
    }
    fun clear() { prefs.edit().remove("recent").apply() }
}
