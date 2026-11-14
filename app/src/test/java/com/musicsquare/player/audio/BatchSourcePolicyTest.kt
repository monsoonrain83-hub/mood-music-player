package com.musicsquare.player.audio

import org.junit.Assert.*
import org.junit.Test

class BatchSourcePolicyTest {
    @Test fun acceptsUnknownSize() { BatchSourcePolicy.checkKnownSize(null) }
    @Test fun acceptsUnavailableNegativeSize() { BatchSourcePolicy.checkKnownSize(-1) }
    @Test fun rejectsEmptyKnownSize() {
        assertThrows(IllegalArgumentException::class.java) { BatchSourcePolicy.checkKnownSize(0) }
    }
    @Test fun acceptsBoundarySize() {
        BatchSourcePolicy.checkKnownSize(AudioFileSafety.MAX_INPUT_BYTES)
    }
    @Test fun rejectsOversizedSource() {
        assertThrows(IllegalArgumentException::class.java) {
            BatchSourcePolicy.checkKnownSize(AudioFileSafety.MAX_INPUT_BYTES+1)
        }
    }
    @Test fun rejectsDuplicateSelection() {
        assertThrows(IllegalArgumentException::class.java) { BatchSourcePolicy.checkSelection(2,1) }
    }
    @Test fun rejectsOversizedSelection() {
        assertThrows(IllegalArgumentException::class.java) { BatchSourcePolicy.checkSelection(201,201) }
    }
    @Test fun acceptsValidSelection() { BatchSourcePolicy.checkSelection(200,200) }
}
