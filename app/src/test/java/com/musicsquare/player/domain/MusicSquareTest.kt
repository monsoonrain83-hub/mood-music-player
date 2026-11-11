package com.musicsquare.player.domain

import org.junit.Assert.*
import org.junit.Test

class MusicSquareTest {
    @Test fun happyEnergeticIsTopRight() {
        assertEquals(4, AudioFeatures(1f, 1f).cellOrNull())
    }
    @Test fun sadCalmIsBottomLeft() {
        assertEquals(20, AudioFeatures(0f, 0f).cellOrNull())
    }
    @Test fun unknownMoodIsNotInvented() {
        assertNull(AudioFeatures(0.5f, null).cellOrNull())
    }
    @Test fun invalidFeaturesAreRejected() {
        assertNull(AudioFeatures(Float.NaN, 0.5f).cellOrNull())
    }
}
