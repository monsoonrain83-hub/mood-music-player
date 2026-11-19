package com.musicsquare.player.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Regression coverage for legacy/corrupt mood preferences during library scans. */
@RunWith(AndroidJUnit4::class)
class MoodStoreInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun validBoundaryCellsRoundTripAndOutOfRangeCellsAreUnclassified() {
        val prefs = context.getSharedPreferences("mood_assignments", Context.MODE_PRIVATE)
        val ids = listOf(-910001L, -910002L, -910003L, -910004L)
        try {
            prefs.edit().putInt(ids[0].toString(), 0)
                .putInt(ids[1].toString(), 24)
                .putInt(ids[2].toString(), -1)
                .putInt(ids[3].toString(), 25).commit()
            val store = MoodStore(context)
            assertEquals(0, store.cell(ids[0]))
            assertEquals(24, store.cell(ids[1]))
            assertNull(store.cell(ids[2]))
            assertNull(store.cell(ids[3]))
        } finally {
            val editor = prefs.edit()
            ids.forEach { editor.remove(it.toString()) }
            editor.commit()
        }
    }

    @Test
    fun corruptPreferenceTypeDoesNotAbortOtherTracks() {
        val prefs = context.getSharedPreferences("mood_assignments", Context.MODE_PRIVATE)
        val badId = -910005L
        val goodId = -910006L
        try {
            prefs.edit().putString(badId.toString(), "legacy-cell")
                .putInt(goodId.toString(), 12).commit()
            val store = MoodStore(context)
            assertNull(store.cell(badId))
            assertEquals(12, store.cell(goodId))
        } finally {
            prefs.edit().remove(badId.toString()).remove(goodId.toString()).commit()
        }
    }
}
