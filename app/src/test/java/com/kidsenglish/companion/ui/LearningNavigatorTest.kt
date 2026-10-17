package com.kidsenglish.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningNavigatorTest {
    @Test fun followsWelcomeConversationLibraryReaderPracticeFlow() {
        val navigator = LearningNavigator()
        assertEquals(AppScreen.WELCOME, navigator.screen)
        navigator.showConversation()
        assertEquals(AppScreen.CONVERSATION, navigator.screen)
        navigator.showLibrary()
        assertEquals(AppScreen.LIBRARY, navigator.screen)
        navigator.showReader()
        assertEquals(AppScreen.READER, navigator.screen)
        navigator.showPractice()
        assertEquals(AppScreen.PRACTICE, navigator.screen)
    }

    @Test fun backReturnsToExpectedParent() {
        val navigator = LearningNavigator(AppScreen.TUTOR)
        navigator.back()
        assertEquals(AppScreen.READER, navigator.screen)
        navigator.back()
        assertEquals(AppScreen.LIBRARY, navigator.screen)
        navigator.back()
        assertEquals(AppScreen.CONVERSATION, navigator.screen)
        navigator.back()
        assertEquals(AppScreen.WELCOME, navigator.screen)
    }
}
