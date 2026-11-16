package app.portero.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.settingsStore by preferencesDataStore(name = "portero_settings")

/** One backend. Several houses mean several servers. */
@Serializable
data class Server(
    val name: String = "",
    val baseUrl: String = "",
    val token: String = "",
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()

    fun normalizedBase(): String = baseUrl.trim().trimEnd('/')

    fun devicesUrl(): String = normalizedBase() + "/api/devices"

    fun stateUrl(): String = normalizedBase() + "/api/state"

    fun eventsUrl(): String = normalizedBase() + "/api/events"

    fun actionUrl(): String = normalizedBase() + "/api/action"

    fun talkUrl(): String = normalizedBase() + "/api/talk"

    fun snapshotUrl(deviceId: String): String =
        normalizedBase() + "/api/devices/" + deviceId + "/snapshot.jpg"

    fun hlsUrl(stream: String): String =
        normalizedBase() + "/go2rtc/api/stream.m3u8?src=" + stream
}

@Serializable
data class Device(
    val id: String = "",
    val name: String = "",
    val kind: String = "camera",
    val camera: String = "",
    val stream: String = "",
) {
    val doorbell: Boolean get() = kind == "doorbell"
}

class SettingsRepository(private val context: Context) {
    private val serversKey = stringPreferencesKey("servers")
    private val legacyUrlKey = stringPreferencesKey("base_url")
    private val legacyTokenKey = stringPreferencesKey("token")
    private val json = Json { ignoreUnknownKeys = true }

    val servers: Flow<List<Server>> = context.settingsStore.data.map { prefs ->
        val raw = prefs[serversKey]
        if (!raw.isNullOrBlank()) {
            runCatching { json.decodeFromString<List<Server>>(raw) }.getOrDefault(emptyList())
        } else {
            // Migrate the single-server settings written by earlier versions.
            val url = prefs[legacyUrlKey].orEmpty()
            val token = prefs[legacyTokenKey].orEmpty()
            if (url.isNotBlank() && token.isNotBlank()) listOf(Server("Casa", url, token)) else emptyList()
        }
    }

    suspend fun save(servers: List<Server>) {
        context.settingsStore.edit { prefs ->
            prefs[serversKey] = json.encodeToString(servers)
        }
    }

    suspend fun upsert(server: Server) {
        val current = serversSnapshot()
        val index = current.indexOfFirst { it.normalizedBase() == server.normalizedBase() }
        val next = if (index >= 0) {
            current.toMutableList().also { it[index] = server }
        } else {
            current + server
        }
        save(next)
    }

    suspend fun remove(baseUrl: String) {
        val next = serversSnapshot().filterNot { it.normalizedBase() == baseUrl.trim().trimEnd('/') }
        save(next)
    }

    private suspend fun serversSnapshot(): List<Server> = servers.first()
}
