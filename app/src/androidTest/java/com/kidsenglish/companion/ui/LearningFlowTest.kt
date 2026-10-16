package com.kidsenglish.companion.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.kidsenglish.companion.MainActivity
import com.kidsenglish.companion.settings.AppSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LearningFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetModelConnection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            AppSettingsRepository(context).setBaseUrl("")
            AppSettingsRepository(context).setAllowDebugHttp(false)
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

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
