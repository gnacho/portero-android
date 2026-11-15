package app.portero

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.portero.data.CallAction
import app.portero.data.PorteroApi
import app.portero.data.Settings
import app.portero.media.MicStreamer
import app.portero.ui.CallUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call

class CallViewModel(
    private val api: PorteroApi,
    private val settings: Settings,
) : ViewModel() {

    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private val mic = MicStreamer()
    private var talkCall: Call? = null
    private var pollJob: Job? = null
    private var wasActive = false

    init {
        poll()
    }

    private fun poll() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val result = runCatching { withContext(Dispatchers.IO) { api.state(settings) } }
                result.onSuccess { event ->
                    _state.value = _state.value.copy(callState = event.call, ringing = event.ringing)
                    // Only end the microphone when a call that was active finishes.
                    // Talking while idle is allowed: it is the intercom.
                    if (wasActive && !event.active) stopTalk("call ended")
                    wasActive = event.active
                }.onFailure {
                    _state.value = _state.value.copy(callState = "sin conexión")
                }
                delay(1_000)
            }
        }
    }

    fun answer() = action(CallAction.ANSWER)

    fun hangup() {
        stopTalk("hangup")
        action(CallAction.HANGUP)
    }

    fun reject() {
        stopTalk("reject")
        action(CallAction.REJECT)
    }

    fun openDoor() = action(CallAction.OPEN)

    private fun action(action: CallAction) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val error = runCatching {
                withContext(Dispatchers.IO) { api.action(settings, action) }
            }.exceptionOrNull()
            _state.value = _state.value.copy(
                busy = false,
                message = when {
                    error != null -> friendlyError(error)
                    action == CallAction.OPEN -> "Puerta abierta"
                    action == CallAction.HANGUP -> "Llamada colgada"
                    action == CallAction.REJECT -> "Llamada rechazada"
                    else -> "Llamada contestada"
                },
            )
        }
    }

    private fun friendlyError(error: Throwable): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("not verified") || text.contains("certificate") ->
                "Certificado no válido: el dominio apunta a otro servidor (DNS)"
            text.contains("Unable to resolve host") ->
                "No se pudo resolver el servidor"
            text.contains("Failed to connect") || text.contains("timeout") ->
                "No se pudo conectar con el servidor"
            text.isBlank() -> error::class.simpleName ?: "Error"
            else -> text.lineSequence().first().take(140)
        }
    }

    fun toggleMic() {
        if (_state.value.microphone) {
            stopTalk("user toggled off")
            return
        }
        if (!mic.start()) {
            _state.value = _state.value.copy(message = "No se pudo abrir el micrófono")
            return
        }
        _state.value = _state.value.copy(microphone = true, message = "Hablando…")
        talkCall = api.startTalk(settings, mic.body()) { error ->
            mic.stop()
            talkCall = null
            _state.value = _state.value.copy(
                microphone = false,
                message = when {
                    error == null -> null
                    error.message == "Canceled" -> "Audio: cancelado"
                    else -> "Audio: ${friendlyError(error)}"
                },
            )
        }
    }

    private fun stopTalk(reason: String = "unknown") {
        android.util.Log.i("CallViewModel", "stopTalk: $reason")
        val call = talkCall
        talkCall = null
        // Stopping the recorder appends the end marker, so the request body
        // finishes by itself and the server closes the doorbell channel
        // cleanly. Cancel only as a fallback if the request does not end.
        mic.stop()
        if (call != null) {
            viewModelScope.launch {
                delay(1_500)
                call.cancel()
            }
        }
        if (_state.value.microphone) {
            _state.value = _state.value.copy(microphone = false)
        }
    }

    override fun onCleared() {
        android.util.Log.i("CallViewModel", "onCleared")
        stopTalk("viewmodel cleared")
        pollJob?.cancel()
        super.onCleared()
    }
}
