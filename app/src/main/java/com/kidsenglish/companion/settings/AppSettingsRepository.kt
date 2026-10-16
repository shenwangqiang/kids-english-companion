package com.kidsenglish.companion.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kidsenglish.companion.model.AgeBand
import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("app_settings")

data class AppSettings(
    val ageBand: AgeBand = AgeBand.AGE_6_8,
    val baseUrl: String = "",
    val modelName: String = "qwen3:4b",
    val speechRate: Float = 0.9f,
    val allowDebugHttp: Boolean = false,
)

class AppSettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { values ->
        AppSettings(
            ageBand = AgeBand.entries.firstOrNull { it.wireValue == values[AGE_BAND] } ?: AgeBand.AGE_6_8,
            baseUrl = values[BASE_URL].orEmpty(),
            modelName = values[MODEL_NAME] ?: "qwen3:4b",
            speechRate = (values[SPEECH_RATE] ?: 0.9f).coerceIn(0.6f, 1.2f),
            allowDebugHttp = values[ALLOW_DEBUG_HTTP] ?: false,
        )
    }

    suspend fun setAgeBand(value: AgeBand) = edit(AGE_BAND, value.wireValue)
    suspend fun setBaseUrl(value: String) = edit(BASE_URL, value.trim())
    suspend fun setModelName(value: String) = edit(MODEL_NAME, value.trim())
    suspend fun setSpeechRate(value: Float) = context.settingsDataStore.edit { it[SPEECH_RATE] = value.coerceIn(0.6f, 1.2f) }
    suspend fun setAllowDebugHttp(value: Boolean) = context.settingsDataStore.edit { it[ALLOW_DEBUG_HTTP] = value }

    private suspend fun edit(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.settingsDataStore.edit { it[key] = value }
    }

    companion object {
        private val AGE_BAND = stringPreferencesKey("age_band")
        private val BASE_URL = stringPreferencesKey("base_url")
        private val MODEL_NAME = stringPreferencesKey("model_name")
        private val SPEECH_RATE = floatPreferencesKey("speech_rate")
        private val ALLOW_DEBUG_HTTP = booleanPreferencesKey("allow_debug_http")
    }
}

object EndpointPolicy {
    fun isAllowed(url: String, isDebug: Boolean, allowDebugHttp: Boolean): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (uri.userInfo != null || uri.fragment != null || uri.host.isNullOrBlank()) return false
        if (uri.scheme.equals("https", ignoreCase = true)) return true
        if (!isDebug || !allowDebugHttp || !uri.scheme.equals("http", ignoreCase = true)) return false
        val host = uri.host.lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host.endsWith(".local") || isPrivateIpv4(host)
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.').mapNotNull(String::toIntOrNull)
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }
}
