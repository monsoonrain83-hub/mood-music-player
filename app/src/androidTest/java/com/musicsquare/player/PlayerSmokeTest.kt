package com.musicsquare.player
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
/** Runs on an emulator/device; catches startup and basic UI regressions. */
class PlayerSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun homeRendersWithoutMusicPermission() {
        rule.onNodeWithText("Your music, your mood").assertIsDisplayed()
    }
}
