package com.musicsquare.player.audio

import org.junit.Assert.*
import org.junit.Test

class AudioEditBoundsTest {
    private fun parse(start:String="0",end:String="20",ranges:String="",duration:Double=30.0) =
        AudioEditInput.parse(start,end,ranges,false,"1",1.0,true,sourceDurationSeconds=duration)

    @Test fun acceptsValidSelection() {
        assertTrue(parse(start="2",end="10",ranges="4-5").isSuccess)
    }
    @Test fun rejectsStartBeyondDuration() {
        assertTrue(parse(start="31",end="",duration=30.0).isFailure)
    }
    @Test fun rejectsEndBeyondDuration() {
        assertTrue(parse(end="31",duration=30.0).isFailure)
    }
    @Test fun rejectsOverlappingCuts() {
        assertTrue(parse(ranges="2-7,6-9").isFailure)
    }
    @Test fun rejectsCutsBeyondDuration() {
        assertTrue(parse(ranges="10-35").isFailure)
    }
    @Test fun rejectsRemovingEntireSelection() {
        assertTrue(parse(ranges="0-20").isFailure)
    }
    @Test fun allowsAdjacentCutRanges() {
        assertTrue(parse(ranges="2-4,4-6").isSuccess)
    }
}
