package com.musicsquare.player.audio

/**
 * Strict, locale-independent validation for the editor's time inputs.
 * Never silently drop a malformed cut interval.
 */
object AudioEditInput {
    fun parse(
        start: String, end: String, ranges: String,
        removeSilence: Boolean, silence: String,
        speed: Double, preservePitch: Boolean
    ): Result<AudioEngine.Edit> = runCatching {
        val from = start.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: if(start.isBlank()) 0.0 else error("Invalid start time")
        require(from.isFinite() && from >= 0.0) { "Start time must be a nonnegative number" }
        val until = if(end.isBlank()) null else end.trim().toDoubleOrNull() ?: error("Invalid end time")
        require(until == null || (until.isFinite() && until > from)) { "End time must be after start" }
        val cuts = if(ranges.isBlank()) emptyList() else ranges.split(",").map { part ->
            val match = Regex("""^\s*(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*$""").matchEntire(part)
                ?: error("Invalid cut range: ${part.trim()}")
            val a = match.groupValues[1].toDouble()
            val b = match.groupValues[2].toDouble()
            require(a.isFinite() && b.isFinite() && a >= 0 && b > a) { "Invalid cut range: ${part.trim()}" }
            require(until == null || a < until) { "Cut range starts after the end time" }
            a to b
        }
        require(cuts.size <= 32) { "Maximum 32 cut ranges" }
        require(speed.isFinite() && speed in 0.25..4.0) { "Speed must be between 0.25× and 4×" }
        val silenceSeconds = if(removeSilence) {
            val value = silence.trim().toDoubleOrNull() ?: error("Invalid silence duration")
            require(value.isFinite() && value in 0.25..30.0) { "Silence duration must be 0.25–30 seconds" }
            value
        } else null
        AudioEngine.Edit(from,until,cuts,silenceSeconds,speed,preservePitch)
    }
}
