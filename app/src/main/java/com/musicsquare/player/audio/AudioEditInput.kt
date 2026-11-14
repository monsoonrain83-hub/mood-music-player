package com.musicsquare.player.audio

/**
 * Strict, locale-independent validation for the editor's time inputs.
 * Never silently drop a malformed cut interval.
 */
object AudioEditInput {
    fun parse(
        start: String, end: String, ranges: String,
        removeSilence: Boolean, silence: String,
        speed: Double, preservePitch: Boolean,
        fadeIn: String = "0", fadeOut: String = "0", normalize: Boolean = false,
        sourceDurationSeconds: Double? = null
    ): Result<AudioEngine.Edit> = runCatching {
        val from = start.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: if(start.isBlank()) 0.0 else error("Invalid start time")
        require(from.isFinite() && from >= 0.0) { "Start time must be a nonnegative number" }
        val until = if(end.isBlank()) null else end.trim().toDoubleOrNull() ?: error("Invalid end time")
        require(until == null || (until.isFinite() && until > from)) { "End time must be after start" }
        if(sourceDurationSeconds!=null) {
            require(sourceDurationSeconds.isFinite() && sourceDurationSeconds>0) { "Invalid source duration" }
            require(from<sourceDurationSeconds) { "Start must be before the file ends" }
            require(until==null || until<=sourceDurationSeconds+0.02) { "End exceeds file duration" }
        }
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
        val sorted=cuts.sortedBy { it.first }
        require(sorted.zipWithNext().all { (a,b) -> a.second<=b.first }) { "Cut ranges cannot overlap" }
        if(sourceDurationSeconds!=null) {
            require(cuts.all { it.second<=sourceDurationSeconds+0.02 }) { "Cut range exceeds file duration" }
        }
        val selectionEnd=until ?: sourceDurationSeconds
        if(selectionEnd!=null && cuts.isNotEmpty()) {
            val inside=sorted.mapNotNull { (a,b) ->
                val x=maxOf(a,from)
                val y=minOf(b,selectionEnd)
                if(y>x)x to y else null
            }
            val removed=inside.sumOf { it.second-it.first }
            require(removed<selectionEnd-from-0.001) { "Cuts would remove the entire selection" }
        }
        require(speed.isFinite() && speed in 0.25..4.0) { "Speed must be between 0.25× and 4×" }
        val silenceSeconds = if(removeSilence) {
            val value = silence.trim().toDoubleOrNull() ?: error("Invalid silence duration")
            require(value.isFinite() && value in 0.25..30.0) { "Silence duration must be 0.25–30 seconds" }
            value
        } else null
        val fadeInValue=fadeIn.trim().toDoubleOrNull() ?: error("Invalid fade-in duration")
        val fadeOutValue=fadeOut.trim().toDoubleOrNull() ?: error("Invalid fade-out duration")
        require(fadeInValue.isFinite() && fadeInValue in 0.0..30.0) { "Fade-in must be 0–30 seconds" }
        require(fadeOutValue.isFinite() && fadeOutValue in 0.0..30.0) { "Fade-out must be 0–30 seconds" }
        require(fadeOutValue==0.0 || (until!=null && until-from>fadeOutValue && cuts.isEmpty() && silenceSeconds==null)) { "Fade-out needs an end time and cannot combine with cuts or silence removal" }
        AudioEngine.Edit(from,until,cuts,silenceSeconds,speed,preservePitch,fadeInValue,fadeOutValue,normalize)
    }
}
