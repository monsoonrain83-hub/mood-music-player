package com.musicsquare.player.audio

import org.junit.Assert.*
import org.junit.Test

class AudioEditInputTest {
    @Test fun parsesValidCutRanges() {
        val result=AudioEditInput.parse("2.5","60","10-15, 25-30",false,"",1.25,true)
        assertTrue(result.isSuccess)
        val edit=result.getOrThrow()
        assertEquals(2.5,edit.startSeconds,0.0001)
        assertEquals(2,edit.removeRanges.size)
        assertEquals(25.0,edit.removeRanges[1].first,0.0001)
    }
    @Test fun rejectsMalformedCutInsteadOfDroppingIt() {
        assertTrue(AudioEditInput.parse("0","","1-2, invalid",false,"",1.0,true).isFailure)
    }
    @Test fun rejectsNonFiniteAndBackwardsTimes() {
        assertTrue(AudioEditInput.parse("NaN","","",false,"",1.0,true).isFailure)
        assertTrue(AudioEditInput.parse("10","5","",false,"",1.0,true).isFailure)
        assertTrue(AudioEditInput.parse("0","","",false,"",Double.POSITIVE_INFINITY,true).isFailure)
    }
    @Test fun rejectsInvalidSilenceDuration() {
        assertTrue(AudioEditInput.parse("0","","",true,"abc",1.0,true).isFailure)
        assertTrue(AudioEditInput.parse("0","","",true,"1.5",1.0,true).isSuccess)
    }
    @Test fun acceptsBlankOptionalFields() {
        assertTrue(AudioEditInput.parse("","","",false,"",1.0,true).isSuccess)
    }
    @Test fun acceptsFadeInAndTimedFadeOut() {
        val edit=AudioEditInput.parse("5","45","",false,"",1.0,true,"2","3").getOrThrow()
        assertEquals(2.0,edit.fadeInSeconds,0.0001)
        assertEquals(3.0,edit.fadeOutSeconds,0.0001)
    }
    @Test fun rejectsFadeOutWithTimelineChangingCuts() {
        assertTrue(AudioEditInput.parse("0","45","10-15",false,"",1.0,true,"0","2").isFailure)
    }
    @Test fun rejectsFadeOutWithoutEndAndInvalidFadeValues() {
        assertTrue(AudioEditInput.parse("0","","",false,"",1.0,true,"0","3").isFailure)
        assertTrue(AudioEditInput.parse("0","20","",false,"",1.0,true,"NaN","0").isFailure)
    }
}
