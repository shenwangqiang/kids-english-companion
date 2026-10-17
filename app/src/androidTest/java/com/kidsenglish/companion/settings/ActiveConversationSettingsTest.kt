package com.kidsenglish.companion.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kidsenglish.companion.model.AgeBand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveConversationSettingsTest {
    @Test
    fun persistsSeparateActiveSessionsWhenRepositoryIsRecreated() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppSettingsRepository(context)
        repository.setActiveSessionId(AgeBand.AGE_6_8, "young-session")
        repository.setActiveSessionId(AgeBand.AGE_9_10, "middle-session")
        repository.setActiveSessionId(AgeBand.AGE_11_12, "older-session")

        val reopened = AppSettingsRepository(context)
        assertEquals("young-session", reopened.activeSessionId(AgeBand.AGE_6_8).first())
        assertEquals("middle-session", reopened.activeSessionId(AgeBand.AGE_9_10).first())
        assertEquals("older-session", reopened.activeSessionId(AgeBand.AGE_11_12).first())
    }
}
