package com.musicsquare.player.audio

import org.junit.Assert.*
import org.junit.Test

class AcousticMoodEstimateTest {
    @Test fun groupsByEnergyWithoutTempo() {
        assertEquals(0.5f, AcousticMoodEstimate.estimate(AudioAnalysis(0.5f, null, 0.9f))!!, 0.0001f)
    }
    @Test fun groupsByEnergyOnLowTempoConfidence() {
        assertEquals(0.5f, AcousticMoodEstimate.estimate(AudioAnalysis(0.5f, 120f, 0.05f))!!, 0.0001f)
    }
    @Test fun estimatesForReliableTempo() {
        val value = AcousticMoodEstimate.estimate(AudioAnalysis(0.7f, 130f, 0.4f))
        assertNotNull(value)
        assertTrue(value!! in 0.15f..0.85f)
    }
    @Test fun fallsBackToEnergyOnInvalidTempoConfidence() {
        for (confidence in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.1f, 1.2f)) {
            assertEquals(0.5f, AcousticMoodEstimate.estimate(AudioAnalysis(0.6f, 120f, confidence))!!, 0.0001f)
        }
    }
    @Test fun usesTempoAtConfidenceThreshold() {
        val result = AcousticMoodEstimate.estimate(AudioAnalysis(0.6f, 160f, 0.18f))
        assertNotNull(result)
        assertTrue(result!! > 0.5f)
    }
    @Test fun fallsBackForInvalidTempoValues() {
        for (bpm in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, 59.9f, 200.1f)) {
            assertEquals(0.5f, AcousticMoodEstimate.estimate(AudioAnalysis(0.6f, bpm, 0.8f))!!, 0.0001f)
        }
    }
    @Test fun tempoConfidenceBoundaryIsExplicit() {
        assertEquals(0.5f, AcousticMoodEstimate.estimate(AudioAnalysis(0.6f, 160f, 0.179f))!!, 0.0001f)
        assertTrue(AcousticMoodEstimate.estimate(AudioAnalysis(0.6f, 160f, 0.18f))!! > 0.5f)
    }
    @Test fun estimatedValenceIsBoundedAtExtremeValidInputs() {
        for (energy in listOf(0f, 1f)) {
            for (bpm in listOf(60f, 200f)) {
                val value = AcousticMoodEstimate.estimate(AudioAnalysis(energy, bpm, 1f))
                assertNotNull(value)
                assertTrue(value!!.isFinite())
                assertTrue(value in 0.15f..0.85f)
            }
        }
    }
    @Test fun missingTempoNeverInventsEmotionalValenceAtEnergyExtremes() {
        // An energy-only fallback must remain in the centre mood column,
        // even for very quiet or loud tracks; energy controls the row instead.
        for (energy in listOf(0f, 0.01f, 0.5f, 0.99f, 1f)) {
            assertEquals(0.5f, AcousticMoodEstimate.estimate(AudioAnalysis(energy, null, 0f))!!, 0.0001f)
        }
    }

    @Test fun reliableTempoOrderingIsMonotonicForSameEnergy() {
        val slow = AcousticMoodEstimate.estimate(AudioAnalysis(0.5f, 60f, 1f))!!
        val medium = AcousticMoodEstimate.estimate(AudioAnalysis(0.5f, 120f, 1f))!!
        val fast = AcousticMoodEstimate.estimate(AudioAnalysis(0.5f, 200f, 1f))!!
        assertTrue(slow < medium)
        assertTrue(medium < fast)
    }

    @Test fun abstainsOnInvalidEnergy() {
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(Float.NaN, 120f, 0.4f)))
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(Float.POSITIVE_INFINITY, 120f, 0.4f)))
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(Float.NEGATIVE_INFINITY, 120f, 0.4f)))
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(-0.1f, 120f, 0.4f)))
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(1.1f, 120f, 0.4f)))
    }
}
