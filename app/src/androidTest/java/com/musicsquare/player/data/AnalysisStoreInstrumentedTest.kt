package com.musicsquare.player.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.musicsquare.player.audio.AudioAnalysis
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Regression tests for the analysis cache used by incremental Mood scans. */
@RunWith(AndroidJUnit4::class)
class AnalysisStoreInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cacheIsScopedToMediaRevisionAndPreservesEnergyOnlyFallback() {
        val prefs = context.getSharedPreferences("audio_analysis_v1", Context.MODE_PRIVATE)
        val id = 9223372036854000000L
        val revision = 1700000000L
        val key = "${id}_${revision}"
        try {
            val store = AnalysisStore(context)
            store.save(id, revision, AudioAnalysis(0.75f, null, 0f))
            val cached = AnalysisStore(context).read(id, revision)
            assertNotNull(cached)
            assertEquals(0.75f, cached!!.energy, 0.0001f)
            assertNull(cached.bpm)
            assertEquals(0f, cached.confidence, 0.0001f)
            assertNull(store.read(id, revision + 1))
        } finally {
            prefs.edit().remove("${key}_energy").remove("${key}_bpm")
                .remove("${key}_confidence").remove("${key}_valence").commit()
        }
    }

    @Test
    fun corruptOptionalTempoAndValenceRetainUsableEnergy() {
        val prefs = context.getSharedPreferences("audio_analysis_v1", Context.MODE_PRIVATE)
        val id = 9223372036854000002L
        val revision = 1700000000L
        val key = "${id}_${revision}"
        try {
            prefs.edit().putFloat("${key}_energy", 0.65f)
                .putString("${key}_bpm", "corrupt")
                .putFloat("${key}_confidence", 0f)
                .putString("${key}_valence", "corrupt").commit()
            val cached = AnalysisStore(context).read(id, revision)
            assertNotNull(cached)
            assertEquals(0.65f, cached!!.energy, 0.0001f)
            assertNull(cached.bpm)
            assertNull(cached.valence)
        } finally {
            prefs.edit().remove("${key}_energy").remove("${key}_bpm")
                .remove("${key}_confidence").remove("${key}_valence").commit()
        }
    }

    @Test
    fun corruptCachedEnergyDoesNotMasqueradeAsCompletedAnalysis() {
        val prefs = context.getSharedPreferences("audio_analysis_v1", Context.MODE_PRIVATE)
        val id = 9223372036854000001L
        val revision = 1700000000L
        val key = "${id}_${revision}"
        try {
            prefs.edit().putFloat("${key}_energy", Float.NaN)
                .putFloat("${key}_confidence", 0.5f).commit()
            assertNull(AnalysisStore(context).read(id, revision))
        } finally {
            prefs.edit().remove("${key}_energy").remove("${key}_bpm")
                .remove("${key}_confidence").remove("${key}_valence").commit()
        }
    }
    @Test
    fun corruptConfidenceDoesNotCountAsSuccessfulMoodAnalysis() {
        val prefs = context.getSharedPreferences("audio_analysis_v1", Context.MODE_PRIVATE)
        val id = 9223372036854000003L
        val revision = 1700000000L
        val key = "${id}_${revision}"
        try {
            // A plausible energy value must not hide an invalid confidence.
            prefs.edit().putFloat("${key}_energy", 0.7f)
                .putFloat("${key}_confidence", Float.NaN).commit()
            assertNull(AnalysisStore(context).read(id, revision))

            prefs.edit().putFloat("${key}_confidence", 1.2f).commit()
            assertNull(AnalysisStore(context).read(id, revision))

            // Older or corrupted preferences can also contain a wrong value type.
            prefs.edit().putString("${key}_confidence", "invalid").commit()
            assertNull(AnalysisStore(context).read(id, revision))
        } finally {
            prefs.edit().remove("${key}_energy").remove("${key}_bpm")
                .remove("${key}_confidence").remove("${key}_valence").commit()
        }
    }

}
