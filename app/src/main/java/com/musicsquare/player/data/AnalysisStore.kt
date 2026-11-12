package com.musicsquare.player.data

import android.content.Context
import com.musicsquare.player.audio.AudioAnalysis

class AnalysisStore(context: Context) {
    private val prefs = context.getSharedPreferences("audio_analysis_v1", Context.MODE_PRIVATE)
    fun read(songId: Long, modified: Long): AudioAnalysis? {
        val key = "${songId}_${modified}"
        if (!prefs.contains("${key}_energy")) return null
        return AudioAnalysis(
            energy=prefs.getFloat("${key}_energy", 0f),
            bpm=prefs.getFloat("${key}_bpm", -1f).takeIf { it > 0 },
            confidence=prefs.getFloat("${key}_confidence", 0f),
            valence=prefs.getFloat("${key}_valence",-1f).takeIf { it in 0f..1f }
        )
    }
    fun save(songId: Long, modified: Long, result: AudioAnalysis) {
        val key = "${songId}_${modified}"
        prefs.edit().putFloat("${key}_energy",result.energy)
            .putFloat("${key}_bpm",result.bpm ?: -1f)
            .putFloat("${key}_confidence",result.confidence)
            .putFloat("${key}_valence",result.valence ?: -1f).apply()
    }
}
