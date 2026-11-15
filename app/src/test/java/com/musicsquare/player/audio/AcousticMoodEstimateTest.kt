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
        for (confidence in listOf(Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.2f)) {
            assertEquals(0.5f, AcousticMoodEstimate.estimate(AudioAnalysis(0.6f, 120f, confidence))!!, 0.0001f)
        }
    }
    @Test fun abstainsOnInvalidEnergy() {
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(Float.NaN, 120f, 0.4f)))
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(-0.1f, 120f, 0.4f)))
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(1.1f, 120f, 0.4f)))
    }
}
