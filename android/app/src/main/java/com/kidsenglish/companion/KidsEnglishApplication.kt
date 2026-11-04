package com.kidsenglish.companion

import android.app.Application
import com.kidsenglish.companion.app.AppContainer

class KidsEnglishApplication : Application() {
    val container by lazy { AppContainer(this) }
}
