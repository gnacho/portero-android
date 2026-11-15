package app.portero.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "portero_settings")

data class Settings(val baseUrl: String, val token: String) {
    val configured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()

    fun normalizedBase(): String = baseUrl.trim().trimEnd('/')

    fun hlsUrl(): String =
        normalizedBase() + "/go2rtc/api/stream.m3u8?src=hikvision-doorbell"

    fun eventsUrl(): String = normalizedBase() + "/api/events"

    fun actionUrl(): String = normalizedBase() + "/api/action"

    fun stateUrl(): String = normalizedBase() + "/api/state"

    fun talkUrl(): String = normalizedBase() + "/api/talk"
}

class SettingsRepository(private val context: Context) {
    private val baseKey = stringPreferencesKey("base_url")
    private val tokenKey = stringPreferencesKey("token")

    val settings: Flow<Settings> = context.settingsStore.data.map { prefs ->
        Settings(
            baseUrl = prefs[baseKey].orEmpty(),
            token = prefs[tokenKey].orEmpty(),
        )
    }

    suspend fun save(baseUrl: String, token: String) {
        context.settingsStore.edit { prefs ->
            prefs[baseKey] = baseUrl.trim().trimEnd('/')
            prefs[tokenKey] = token.trim()
        }
    }
}
