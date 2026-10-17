package com.kidsenglish.companion.ui

enum class AppScreen { WELCOME, CONVERSATION, LIBRARY, READER, PRACTICE, TUTOR, PROGRESS, SETTINGS }

class LearningNavigator(initial: AppScreen = AppScreen.WELCOME) {
    var screen: AppScreen = initial
        private set

    fun showConversation() { screen = AppScreen.CONVERSATION }
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
            AppScreen.LIBRARY, AppScreen.PROGRESS, AppScreen.SETTINGS -> AppScreen.CONVERSATION
            AppScreen.READER -> AppScreen.LIBRARY
            AppScreen.PRACTICE, AppScreen.TUTOR -> AppScreen.READER
        }
    }
}
