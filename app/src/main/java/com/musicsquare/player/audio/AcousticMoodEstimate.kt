package com.musicsquare.player.audio

/**
 * Explicitly provisional acoustic grouping, NOT a validated emotion/valence classifier.
 * Only use when a reliable tempo estimate exists; otherwise abstain.
 */
object AcousticMoodEstimate {
    fun estimate(analysis: AudioAnalysis): Float? {
        val bpm=analysis.bpm ?: return null
        if(!bpm.isFinite() || bpm !in 60f..200f || analysis.confidence < 0.18f ||
            !analysis.energy.isFinite() || analysis.energy !in 0f..1f) return null
        // Positive arousal/tempo is not equivalent to happiness.
        // This is an approximate listening-vibe proxy used for optional discovery.
        return (0.50f + (bpm-120f)/400f + (analysis.energy-0.5f)*0.20f).coerceIn(0.15f,0.85f)
    }
}
