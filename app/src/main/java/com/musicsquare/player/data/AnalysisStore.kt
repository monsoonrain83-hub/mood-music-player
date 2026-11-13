package com.musicsquare.player.data

import android.content.Context
import com.musicsquare.player.audio.AudioAnalysis

/**
 * Cache keys include the MediaStore song id and source revision.
 * Reject corrupted/legacy non-finite values instead of treating them as valid analysis.
 */
class AnalysisStore(context: Context) {
    private val prefs = context.getSharedPreferences("audio_analysis_v1", Context.MODE_PRIVATE)

    fun read(songId: Long, modified: Long): AudioAnalysis? {
        if (songId < 0 || modified < 0) return null
        val key = "${songId}_${modified}"
        if (!prefs.contains("${key}_energy")) return null
        return runCatching {
            val energy = prefs.getFloat("${key}_energy", Float.NaN)
            val bpmValue = prefs.getFloat("${key}_bpm", -1f)
            val confidence = prefs.getFloat("${key}_confidence", 0f)
            val valenceValue = prefs.getFloat("${key}_valence", -1f)
            if (!energy.isFinite() || energy !in 0f..1f ||
                !confidence.isFinite() || confidence !in 0f..1f) return null
            AudioAnalysis(
                energy = energy,
                bpm = bpmValue.takeIf { it.isFinite() && it in 40f..260f },
                confidence = confidence,
                valence = valenceValue.takeIf { it.isFinite() && it in 0f..1f }
            )
        }.getOrNull()
    }

    fun save(songId: Long, modified: Long, result: AudioAnalysis) {
        if (songId < 0 || modified < 0 || !result.energy.isFinite() ||
            result.energy !in 0f..1f || !result.confidence.isFinite() ||
            result.confidence !in 0f..1f) return
        val key = "${songId}_${modified}"
        prefs.edit().putFloat("${key}_energy", result.energy)
            .putFloat("${key}_bpm", result.bpm?.takeIf { it.isFinite() && it in 40f..260f } ?: -1f)
            .putFloat("${key}_confidence", result.confidence)
            .putFloat("${key}_valence", result.valence?.takeIf { it.isFinite() && it in 0f..1f } ?: -1f)
            .apply()
    }
}
