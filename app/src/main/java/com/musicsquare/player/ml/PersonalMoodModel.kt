package com.musicsquare.player.ml

import kotlin.math.abs
import kotlin.math.ln

/**
 * On-device, user-supervised, distance-weighted nearest-neighbour model.
 *
 * Not a pretrained emotion recogniser. Only explicitly labelled songs provide
 * supervision; BPM and RMS-energy are acoustic proxies, not emotional truth.
 * Predictions abstain if training diversity or local support is insufficient.
 */
object PersonalMoodModel {
    data class Example(val songId: Long, val energy: Float, val bpm: Float?, val cell: Int)
    data class Prediction(val cell: Int, val confidence: Float, val support: Int)

    fun predict(energy: Float, bpm: Float?, examples: List<Example>, excludedId: Long? = null): Prediction? {
        if (!energy.isFinite() || energy !in 0f..1f || bpm != null && (!bpm.isFinite() || bpm !in 40f..260f)) return null
        val valid = examples.filter { it.songId != excludedId &&
            it.cell in 0..24 && it.energy.isFinite() && it.energy in 0f..1f &&
            (it.bpm == null || it.bpm.isFinite() && it.bpm in 40f..260f) }
            .distinctBy { it.songId }
        if (valid.size < 5 || valid.map { it.cell }.distinct().size < 2) return null
        fun distance(e: Example): Double {
            val energyDistance = abs(energy - e.energy).toDouble()
            // Unknown tempo is not evidence of tempo similarity.
            val tempoDistance = if (bpm != null && e.bpm != null)
                abs(ln(bpm.toDouble() / e.bpm.toDouble())) / ln(2.0) else 0.4
            return 0.70 * energyDistance + 0.30 * tempoDistance.coerceAtMost(1.0)
        }
        val nearest = valid.map { it to distance(it) }.sortedBy { it.second }.take(5)
        if (nearest.first().second > 0.23) return null
        val votes = nearest.groupBy { it.first.cell }.mapValues { (_, group) ->
            group.sumOf { (_, distance) -> 1.0 / (0.06 + distance) }
        }
        val winner = votes.maxByOrNull { it.value } ?: return null
        val total = votes.values.sum()
        val confidence = (winner.value / total).toFloat()
        // Prevent unearned confident guesses with contradictory neighbours.
        if (confidence < 0.55f) return null
        return Prediction(winner.key, confidence, nearest.count { it.first.cell == winner.key })
    }
}
