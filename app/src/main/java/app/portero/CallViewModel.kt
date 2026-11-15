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
                    if (wasActive && !event.active) stopTalk()
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
        stopTalk()
        action(CallAction.HANGUP)
    }

    fun reject() {
        stopTalk()
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
                    error != null -> "Error: ${error.message}"
                    action == CallAction.OPEN -> "Puerta abierta"
                    action == CallAction.HANGUP -> "Llamada colgada"
                    action == CallAction.REJECT -> "Llamada rechazada"
                    else -> "Llamada contestada"
                },
            )
        }
    }

    fun toggleMic() {
        if (_state.value.microphone) {
            stopTalk()
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
                    else -> "Audio: ${error.message}"
                },
            )
        }
    }

    private fun stopTalk() {
        talkCall?.cancel()
        talkCall = null
        mic.stop()
        if (_state.value.microphone) {
            _state.value = _state.value.copy(microphone = false)
        }
    }

    override fun onCleared() {
        stopTalk()
        pollJob?.cancel()
        super.onCleared()
    }
}
