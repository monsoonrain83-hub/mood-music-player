package com.musicsquare.player.audio

import org.junit.Assert.*
import org.junit.Test

class AcousticMoodEstimateTest {
    @Test fun abstainsWithoutTempo() {
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(0.5f,null,0.9f)))
    }
    @Test fun abstainsOnLowConfidence() {
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(0.5f,120f,0.05f)))
    }
    @Test fun estimatesForReliableTempo() {
        val value=AcousticMoodEstimate.estimate(AudioAnalysis(0.7f,130f,0.4f))
        assertNotNull(value)
        assertTrue(value!! in 0.15f..0.85f)
    }
    @Test fun abstainsOnInvalidEnergy() {
        assertNull(AcousticMoodEstimate.estimate(AudioAnalysis(Float.NaN,120f,0.4f)))
    }
}
