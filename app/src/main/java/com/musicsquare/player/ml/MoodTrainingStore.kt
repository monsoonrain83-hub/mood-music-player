package com.musicsquare.player.ml

import android.content.Context
import com.musicsquare.player.data.AnalysisStore
import com.musicsquare.player.data.LocalSong

/**
 * Stores explicit mood labels on the device only. Tracks previously placed
 * automatically are never silently promoted to human training examples.
 */
class MoodTrainingStore(context: Context) {
    private val preferences = context.getSharedPreferences("manual_mood_training_v1", Context.MODE_PRIVATE)

    fun label(songId: Long, cell: Int) {
        require(cell in 0..24)
        preferences.edit().putInt(songId.toString(), cell).apply()
    }

    fun exampleCount(): Int = preferences.all.values.count { it is Int && it in 0..24 }

    fun examples(songs: List<LocalSong>, analyses: AnalysisStore): List<PersonalMoodModel.Example> =
        songs.mapNotNull { song ->
            // A malformed legacy preference must not discard valid training labels.
            val cell = try {
                preferences.getInt(song.id.toString(), -1).takeIf { it in 0..24 }
            } catch (error: ClassCastException) {
                android.util.Log.w("MusicSquareMood", "Skipping malformed manual label for song ${song.id}", error)
                null
            } ?: return@mapNotNull null
            // One corrupt or unreadable cached analysis must not discard the other
            // manually labelled examples or abort the entire training batch.
            val features = try {
                analyses.read(song.id, song.modified)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MusicSquareMood", "Skipping unreadable training cache for song ${song.id}", error)
                null
            } ?: return@mapNotNull null
            // A corrupted cache entry must not poison the personal model for
            // every song in the batch. Keep valid energy-only examples usable.
            if (!features.energy.isFinite() || features.energy !in 0f..1f) return@mapNotNull null
            val usableBpm = features.bpm?.takeIf { it.isFinite() && it in 60f..200f }
            PersonalMoodModel.Example(song.id, features.energy, usableBpm, cell)
        }

    fun clear() = preferences.edit().clear().apply()
}
