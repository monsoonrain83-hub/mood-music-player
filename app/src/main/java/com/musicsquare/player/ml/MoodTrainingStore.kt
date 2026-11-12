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
            val cell = preferences.getInt(song.id.toString(), -1).takeIf { it in 0..24 } ?: return@mapNotNull null
            val features = analyses.read(song.id, song.duration) ?: return@mapNotNull null
            PersonalMoodModel.Example(song.id, features.energy, features.bpm, cell)
        }

    fun clear() = preferences.edit().clear().apply()
}
