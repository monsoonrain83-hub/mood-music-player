package com.musicsquare.player.tags

import org.junit.Assert.*
import org.junit.Test

class TagInputValidatorTest {
    @Test fun validFieldsPass() {
        assertTrue(TagInputValidator.validate(TagChanges(title="Example",year="2026",track="3/12")).isSuccess)
    }
    @Test fun emptyChangesFail() {
        assertTrue(TagInputValidator.validate(TagChanges()).isFailure)
    }
    @Test fun malformedYearFails() {
        assertTrue(TagInputValidator.validate(TagChanges(year="20xx")).isFailure)
    }
    @Test fun invalidTrackFails() {
        assertTrue(TagInputValidator.validate(TagChanges(track="0/12")).isFailure)
    }
    @Test fun controlCharactersFail() {
        assertTrue(TagInputValidator.validate(TagChanges(title="Bad\u0000Title")).isFailure)
    }
    @Test fun excessivelyLongFieldFails() {
        assertTrue(TagInputValidator.validate(TagChanges(comment="x".repeat(4097))).isFailure)
    }
    @Test fun blankFieldCanClearExistingTag() {
        assertTrue(TagInputValidator.validate(TagChanges(title="")).isSuccess)
    }
}
