package com.kidsenglish.companion.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kidsenglish.companion.MainActivity
import org.junit.Rule
import org.junit.Test

class LearningFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun chooseBandOpenStoryAndEnterPractice() {
        compose.onNodeWithText("Ages 6–8").performClick()
        compose.onNodeWithText("Mia and the Red Kite").performClick()
        compose.onNodeWithText("Practice this sentence").performClick()
        compose.onNodeWithText("Hold to speak").assertIsDisplayed()
    }

    @Test fun unavailableTutorDoesNotBlockReading() {
        compose.onNodeWithText("Ages 6–8").performClick()
        compose.onNodeWithText("Mia and the Red Kite").performClick()
        compose.onNodeWithText("Ask about this story").performClick()
        compose.onNodeWithText("AI tutor is offline").assertIsDisplayed()
    }
}
