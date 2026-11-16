package app.portero.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

class PorteroApi(private val client: OkHttpClient = defaultClient()) {

    private val json = Json { ignoreUnknownKeys = true }

    fun devices(server: Server): List<Device> {
        val request = Request.Builder()
            .url(server.devicesUrl())
            .header("Authorization", "Bearer ${server.token}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("devices ${response.code}")
            return json.decodeFromString<List<Device>>(response.body.string())
        }
    }

    fun snapshot(server: Server, deviceId: String): ByteArray {
        val request = Request.Builder()
            .url(server.snapshotUrl(deviceId))
            .header("Authorization", "Bearer ${server.token}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("snapshot ${response.code}")
            return response.body.bytes()
        }
    }

    fun events(server: Server): Flow<CallEvent> = callbackFlow {
        val request = Request.Builder()
            .url(server.eventsUrl())
            .header("Authorization", "Bearer ${server.token}")
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val parsed = runCatching { json.decodeFromString<CallEvent>(data) }.getOrNull()
                if (parsed != null) trySend(parsed)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IOException("events stream failed (${response?.code})"))
            }
        }

        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    suspend fun state(server: Server): CallEvent {
        val request = Request.Builder()
            .url(server.stateUrl())
            .header("Authorization", "Bearer ${server.token}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("state ${response.code}")
            val body = response.body.string()
            return json.decodeFromString<CallEvent>(body)
        }
    }

    suspend fun action(server: Server, action: CallAction) {
        val payload = """{"action":"${action.wire}"}""".toRequestBody(JSON)
        val request = Request.Builder()
            .url(server.actionUrl())
            .header("Authorization", "Bearer ${server.token}")
            .post(payload)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("action ${response.code}")
        }
    }

    fun talkRequest(server: Server, body: RequestBody): Request =
        Request.Builder()
            .url(server.talkUrl())
            .header("Authorization", "Bearer ${server.token}")
            .header("Content-Type", "audio/basic")
            .post(body)
            .build()

    fun startTalk(server: Server, body: RequestBody, onDone: (Throwable?) -> Unit): Call {
        android.util.Log.i("PorteroApi", "talk: opening ${server.talkUrl()}")
        val call = client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .newCall(talkRequest(server, body))
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.w("PorteroApi", "talk failed: ${e::class.simpleName}: ${e.message}", e)
                onDone(e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    android.util.Log.i("PorteroApi", "talk closed with ${it.code}")
                    onDone(if (it.isSuccessful) null else IOException("talk ${it.code}"))
                }
            }
        })
        return call
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
