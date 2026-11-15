package com.musicsquare.player.audio

/**
 * Acoustic listening-vibe proxy, not a validated emotion/valence detector.
 * With usable BPM, use tempo and energy; otherwise group by loudness energy
 * on the centre column. Never label this estimate as measured happiness.
 */
object AcousticMoodEstimate {
    fun estimate(analysis: AudioAnalysis): Float? {
        // Energy is the minimum usable signal. A broken tempo-confidence value
        // should disable BPM-based placement, not discard a readable song.
        if (!analysis.energy.isFinite() || analysis.energy !in 0f..1f) return null
        val bpm = analysis.bpm
        val confidence = analysis.confidence
        if (bpm == null || !bpm.isFinite() || bpm !in 60f..200f ||
            !confidence.isFinite() || confidence !in 0f..1f || confidence < 0.18f) {
            return 0.5f
        }
        return (0.50f + (bpm - 120f) / 400f + (analysis.energy - 0.5f) * 0.20f)
            .coerceIn(0.15f, 0.85f)
    }
}
