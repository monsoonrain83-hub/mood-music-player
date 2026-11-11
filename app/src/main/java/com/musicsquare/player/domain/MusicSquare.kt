package com.musicsquare.player.domain

/** Five columns increase in energy; five rows decrease in valence (happiness). */
data class AudioFeatures(
    val energy: Float?,
    val valence: Float?,
    val tempoBpm: Float? = null,
    val confidence: Float = 0f
) {
    fun cellOrNull(): Int? {
        val e = energy?.takeIf { it.isFinite() && it in 0f..1f } ?: return null
        val v = valence?.takeIf { it.isFinite() && it in 0f..1f } ?: return null
        val col = (e * 5).toInt().coerceIn(0, 4)
        val row = ((1f - v) * 5).toInt().coerceIn(0, 4)
        return row * 5 + col
    }
}
