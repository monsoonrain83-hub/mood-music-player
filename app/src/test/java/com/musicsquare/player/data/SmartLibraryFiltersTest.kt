package com.musicsquare.player.data

import com.musicsquare.player.audio.AudioAnalysis
import org.junit.Assert.*
import org.junit.Test

class SmartLibraryFiltersTest {
    @Test fun energeticThresholdIsInclusive() {
        assertTrue(SmartLibraryFilters.matches("Energetic", AudioAnalysis(.70f, 90f, .8f)))
        assertFalse(SmartLibraryFilters.matches("Energetic", AudioAnalysis(.69f, 90f, .8f)))
    }

    @Test fun relaxedRequiresLowEnergyAndNonFastTempo() {
        assertTrue(SmartLibraryFilters.matches("Relaxed", AudioAnalysis(.20f, 100f, .5f)))
        assertTrue(SmartLibraryFilters.matches("Relaxed", AudioAnalysis(.20f, null, 0f)))
        assertFalse(SmartLibraryFilters.matches("Relaxed", AudioAnalysis(.20f, 150f, .5f)))
        assertFalse(SmartLibraryFilters.matches("Relaxed", AudioAnalysis(.60f, 80f, .5f)))
    }

    @Test fun fastTempoRequiresMeasuredBpm() {
        assertTrue(SmartLibraryFilters.matches("Fast tempo", AudioAnalysis(.4f, 130f, .5f)))
        assertFalse(SmartLibraryFilters.matches("Fast tempo", AudioAnalysis(.4f, null, 0f)))
        assertFalse(SmartLibraryFilters.matches("Fast tempo", AudioAnalysis(.4f, 129f, .5f)))
    }

    @Test fun unanalyzedNeverInfersMood() {
        assertTrue(SmartLibraryFilters.matches("Unanalyzed", null))
        assertFalse(SmartLibraryFilters.matches("Energetic", null))
        assertFalse(SmartLibraryFilters.matches("Relaxed", null))
        assertFalse(SmartLibraryFilters.matches("Fast tempo", null))
    }

    @Test fun nonFiniteFeaturesCannotMatch() {
        assertFalse(SmartLibraryFilters.matches("Energetic", AudioAnalysis(Float.NaN, 140f, .5f)))
        assertFalse(SmartLibraryFilters.matches("Fast tempo", AudioAnalysis(.8f, Float.POSITIVE_INFINITY, .5f)))
    }
}
