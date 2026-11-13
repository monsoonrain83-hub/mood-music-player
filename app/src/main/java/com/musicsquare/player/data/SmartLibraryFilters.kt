package com.musicsquare.player.data

import com.musicsquare.player.audio.AudioAnalysis

/**
 * Explainable local library filters. These are acoustic heuristics, not
 * machine-learned emotion labels. Null analysis never implies a mood.
 */
object SmartLibraryFilters {
    fun matches(view: String, analysis: AudioAnalysis?): Boolean = when (view) {
        "Unanalyzed" -> analysis == null
        "Energetic" -> analysis?.energy?.let { it.isFinite() && it >= 0.70f } == true
        "Relaxed" -> analysis?.let {
            it.energy.isFinite() && it.energy <= 0.35f &&
                (it.bpm == null || it.bpm.isFinite() && it.bpm <= 105f)
        } == true
        "Fast tempo" -> analysis?.bpm?.let { it.isFinite() && it >= 130f } == true
        else -> false
    }
}
