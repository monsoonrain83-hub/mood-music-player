package com.musicsquare.player.ml

import org.junit.Assert.*
import org.junit.Test

class PersonalMoodModelTest {
    private val training = listOf(
        PersonalMoodModel.Example(1, .10f, 70f, 20),
        PersonalMoodModel.Example(2, .12f, 72f, 20),
        PersonalMoodModel.Example(3, .14f, 74f, 20),
        PersonalMoodModel.Example(4, .82f, 155f, 4),
        PersonalMoodModel.Example(5, .86f, 160f, 4),
        PersonalMoodModel.Example(6, .88f, 165f, 4)
    )
    @Test fun learnsFromHumanLabels() {
        val result = PersonalMoodModel.predict(.13f, 72f, training)
        assertNotNull(result)
        assertEquals(20, result!!.cell)
        assertTrue(result.confidence >= .55f)
    }
    @Test fun abstainsWithInsufficientLabels() {
        assertNull(PersonalMoodModel.predict(.15f, 72f, training.take(3)))
    }
    @Test fun abstainsWithoutLabelDiversity() {
        assertNull(PersonalMoodModel.predict(.15f, 72f, training.take(3) + training.take(3).map { it.copy(songId = it.songId + 10) }))
    }
    @Test fun excludesSongItselfFromTraining() {
        val onlySix = training.take(5)
        assertNull(PersonalMoodModel.predict(.11f, 71f, onlySix, excludedId = 1))
    }
    @Test fun rejectsInvalidInputs() {
        assertNull(PersonalMoodModel.predict(Float.NaN, 72f, training))
        assertNull(PersonalMoodModel.predict(.5f, Float.POSITIVE_INFINITY, training))
        assertNull(PersonalMoodModel.predict(-1f, 70f, training))
    }
    @Test fun cannotGuessUnsupportedAudio() {
        assertNull(PersonalMoodModel.predict(.49f, 110f, training))
    }
    @Test fun duplicateSongCannotPadTraining() {
        val padded = List(12) { training[0] } + training[3]
        assertNull(PersonalMoodModel.predict(.1f, 70f, padded))
    }
}
