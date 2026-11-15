package app.portero.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CallEvent(
    val call: String = "idle",
    val camera: String = "unknown",
    val relay: String = "off",
    val online: Boolean = false,
    @SerialName("updated_at") val updatedAt: String = "",
    val errors: List<String> = emptyList(),
) {
    /** A call is active for anything the device does not report as idle. */
    val active: Boolean
        get() = call.isNotBlank() && call.lowercase() !in IDLE

    val ringing: Boolean
        get() = call.lowercase() == "ringing" || call.lowercase() == "ring"

    companion object {
        private val IDLE = setOf("idle", "unknown", "unavailable", "off", "")
    }
}

enum class CallAction(val wire: String) {
    ANSWER("answer"),
    HANGUP("hangup"),
    REJECT("reject"),
    OPEN("open"),
}
