package com.kidsenglish.companion.speech

import android.content.Intent
import android.speech.RecognitionService
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechServiceVisibilityTest {
    @Test fun standardRecognitionServiceIsVisibleToTheApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = context.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE),
            0,
        )

        assertTrue("Expected at least one visible Android RecognitionService", services.isNotEmpty())
    }
}
