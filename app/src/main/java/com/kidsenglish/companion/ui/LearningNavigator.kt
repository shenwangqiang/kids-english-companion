package com.kidsenglish.companion.ui

enum class AppScreen { WELCOME, LIBRARY, READER, PRACTICE, TUTOR, PROGRESS, SETTINGS }

class LearningNavigator(initial: AppScreen = AppScreen.WELCOME) {
    var screen: AppScreen = initial
        private set

    fun showLibrary() { screen = AppScreen.LIBRARY }
    fun showReader() { screen = AppScreen.READER }
    fun showPractice() { screen = AppScreen.PRACTICE }
    fun showTutor() { screen = AppScreen.TUTOR }
    fun showProgress() { screen = AppScreen.PROGRESS }
    fun showSettings() { screen = AppScreen.SETTINGS }

    fun back() {
        screen = when (screen) {
            AppScreen.WELCOME -> AppScreen.WELCOME
            AppScreen.LIBRARY -> AppScreen.WELCOME
            AppScreen.READER, AppScreen.PROGRESS, AppScreen.SETTINGS -> AppScreen.LIBRARY
            AppScreen.PRACTICE, AppScreen.TUTOR -> AppScreen.READER
        }
    }
}
