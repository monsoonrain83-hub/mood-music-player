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
    @Test fun resetClearsRecoveryState() {
        val policy = PlaybackFailurePolicy(1)
        assertTrue(policy.onFailure(true))
        policy.reset()
        assertEquals(0, policy.consecutiveFailures)
        assertTrue(policy.onFailure(true))
    }

    @Test fun countTracksFailuresEvenWithoutNextItem() {
        val policy = PlaybackFailurePolicy(2)
        assertFalse(policy.onFailure(false))
        assertEquals(1, policy.consecutiveFailures)
        assertTrue(policy.onFailure(true))
        assertFalse(policy.onFailure(true))
    }
}
