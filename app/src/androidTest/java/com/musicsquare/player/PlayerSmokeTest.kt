package com.musicsquare.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/**
 * Test application startup plus all five primary navigation destinations.
 * Does not require the user to grant READ_MEDIA_AUDIO.
 */
class PlayerSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun homeRendersWithoutMusicPermission() {
        rule.onNodeWithText("Your music, your mood").assertIsDisplayed()
    }

    @Test fun primaryNavigationRemainsReachable() {
        rule.onNodeWithContentDescription("Mood").performClick()
        rule.onNodeWithText("Find your mood").assertIsDisplayed()
        rule.onNodeWithContentDescription("Studio").performClick()
        rule.onNodeWithText("Audio Studio").assertIsDisplayed()
        rule.onNodeWithContentDescription("Player").performClick()
        rule.onNodeWithContentDescription("Library").performClick()
        rule.onNodeWithContentDescription("Home").performClick()
        rule.onNodeWithText("Your music, your mood").assertIsDisplayed()
    }
}
