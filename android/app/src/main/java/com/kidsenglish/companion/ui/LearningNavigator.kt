package com.kidsenglish.companion.ui

enum class AppScreen { WELCOME, CONVERSATION, TOPICS, HISTORY, LIBRARY, READER, PRACTICE, TUTOR, PROGRESS, SETTINGS }

class LearningNavigator(initial: AppScreen = AppScreen.WELCOME) {
    var screen: AppScreen = initial
        private set

    fun showConversation() { screen = AppScreen.CONVERSATION }
    fun showTopicPicker() { screen = AppScreen.TOPICS }
    fun showHistory() { screen = AppScreen.HISTORY }
    fun showLibrary() { screen = AppScreen.LIBRARY }
    fun showReader() { screen = AppScreen.READER }
    fun showPractice() { screen = AppScreen.PRACTICE }
    fun showTutor() { screen = AppScreen.TUTOR }
    fun showProgress() { screen = AppScreen.PROGRESS }
    fun showSettings() { screen = AppScreen.SETTINGS }

    fun back() {
        screen = when (screen) {
            AppScreen.WELCOME -> AppScreen.WELCOME
            AppScreen.CONVERSATION -> AppScreen.WELCOME
            AppScreen.TOPICS, AppScreen.HISTORY, AppScreen.LIBRARY, AppScreen.PROGRESS, AppScreen.SETTINGS -> AppScreen.CONVERSATION
            AppScreen.READER -> AppScreen.LIBRARY
            AppScreen.PRACTICE, AppScreen.TUTOR -> AppScreen.READER
        }
    }
}
