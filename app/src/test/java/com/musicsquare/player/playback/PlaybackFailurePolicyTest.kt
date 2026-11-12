package com.musicsquare.player.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackFailurePolicyTest {
    @Test fun skipsOnlyUpToLimit() {
        val policy = PlaybackFailurePolicy(3)
        assertTrue(policy.onFailure(true))
        assertTrue(policy.onFailure(true))
        assertTrue(policy.onFailure(true))
        assertFalse(policy.onFailure(true))
        assertFalse(policy.onFailure(true))
    }

    @Test fun neverSkipsWithoutNextItem() {
        assertFalse(PlaybackFailurePolicy().onFailure(false))
    }

    @Test fun readyResetsConsecutiveFailureCounter() {
        val policy = PlaybackFailurePolicy(1)
        assertTrue(policy.onFailure(true))
        assertFalse(policy.onFailure(true))
        policy.onReady()
        assertTrue(policy.onFailure(true))
    }

    @Test fun invalidLimitRejected() {
        try {
            PlaybackFailurePolicy(0)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }
}
