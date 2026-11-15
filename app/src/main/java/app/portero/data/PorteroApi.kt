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

    fun events(settings: Settings): Flow<CallEvent> = callbackFlow {
        val request = Request.Builder()
            .url(settings.eventsUrl())
            .header("Authorization", "Bearer ${settings.token}")
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

    suspend fun state(settings: Settings): CallEvent {
        val request = Request.Builder()
            .url(settings.stateUrl())
            .header("Authorization", "Bearer ${settings.token}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("state ${response.code}")
            val body = response.body.string()
            return json.decodeFromString<CallEvent>(body)
        }
    }

    suspend fun action(settings: Settings, action: CallAction) {
        val payload = """{"action":"${action.wire}"}""".toRequestBody(JSON)
        val request = Request.Builder()
            .url(settings.actionUrl())
            .header("Authorization", "Bearer ${settings.token}")
            .post(payload)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("action ${response.code}")
        }
    }

    fun talkRequest(settings: Settings, body: RequestBody): Request =
        Request.Builder()
            .url(settings.talkUrl())
            .header("Authorization", "Bearer ${settings.token}")
            .header("Content-Type", "audio/basic")
            .post(body)
            .build()

    fun startTalk(settings: Settings, body: RequestBody, onDone: (Throwable?) -> Unit): Call {
        val call = client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .build()
            .newCall(talkRequest(settings, body))
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onDone(e)
            override fun onResponse(call: Call, response: Response) {
                response.use {
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
