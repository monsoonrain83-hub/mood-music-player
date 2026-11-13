package com.musicsquare.player.playback

/**
 * Limits automatic recovery from broken local media. Never loops forever on a
 * corrupt playlist or retries the same unreadable item indefinitely.
 *
 * The counter resets only after a player reaches READY, not merely when a
 * new item is selected (which may also be broken).
 */
internal class PlaybackFailurePolicy(private val maxConsecutiveFailures: Int = 3) {
    init { require(maxConsecutiveFailures > 0) }
    private var failures = 0

    fun onReady() { failures = 0 }

    val consecutiveFailures: Int get() = failures

    fun onFailure(hasNext: Boolean): Boolean {
        if (failures < Int.MAX_VALUE) failures++
        return hasNext && failures <= maxConsecutiveFailures
    }

    fun reset() { failures = 0 }
}
