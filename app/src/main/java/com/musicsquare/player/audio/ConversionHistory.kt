package com.musicsquare.player.audio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small, bounded on-device history. Stores counts, not source paths or URIs. */
class ConversionHistory(context:Context) {
    private val prefs=context.getSharedPreferences("conversion_history",Context.MODE_PRIVATE)
    data class Entry(val timestamp:Long,val format:String,val completed:Int,val failed:Int)
    @Synchronized fun recent():List<Entry> = runCatching {
        val array=JSONArray(prefs.getString("recent","[]"))
        (0 until minOf(array.length(),20)).mapNotNull { index ->
            runCatching {
                val obj=array.getJSONObject(index)
                val entry=Entry(obj.getLong("time"),obj.getString("format"),
                    obj.getInt("ok"),obj.getInt("failed"))
                entry.takeIf { it.timestamp>0 && it.completed>=0 && it.failed>=0 &&
                    it.format.matches(Regex("[a-zA-Z0-9]{1,10}")) }
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
    @Synchronized fun add(format:AudioEngine.Format,completed:Int,failed:Int) {
        require(completed>=0 && failed>=0) { "Conversion counts cannot be negative" }
        val next=listOf(Entry(System.currentTimeMillis(),format.ext,completed,failed))+recent()
        val array=JSONArray()
        next.take(20).forEach { entry ->
            array.put(JSONObject().put("time",entry.timestamp).put("format",entry.format)
                .put("ok",entry.completed).put("failed",entry.failed))
        }
        prefs.edit().putString("recent",array.toString()).commit()
    }
    @Synchronized fun clear() { prefs.edit().remove("recent").commit() }
}
