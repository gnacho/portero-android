package app.portero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class CallUiState(
    val callState: String = "unknown",
    val microphone: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

@Composable
fun CallScreen(
    url: String,
    token: String,
    state: CallUiState,
    onAnswer: () -> Unit,
    onHangup: () -> Unit,
    onReject: () -> Unit,
    onOpenDoor: () -> Unit,
    onToggleMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(url = url, token = token, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Estado: ${state.callState}",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        state.message?.let {
            Text(it, color = Color(0xFFFFB4A8), modifier = Modifier.padding(horizontal = 16.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton("Contestar", Icons.Filled.Call, Color(0xFF2FBF71), enabled = !state.busy, onClick = onAnswer, modifier = Modifier.weight(1f))
            ActionButton("Colgar", Icons.Filled.CallEnd, Color(0xFFE0574F), enabled = !state.busy, onClick = onHangup, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton("Rechazar", Icons.Filled.PhoneDisabled, Color(0xFFE0574F), enabled = !state.busy, onClick = onReject, modifier = Modifier.weight(1f))
            ActionButton("Abrir", Icons.Filled.LockOpen, Color(0xFFE0A03A), enabled = !state.busy, onClick = onOpenDoor, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(
                onClick = onToggleMic,
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        if (state.microphone) Color(0xFF2FBF71) else Color(0xFF1B232D),
                        CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = if (state.microphone) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = "Micrófono",
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.microphone) "Hablando…" else "Micrófono apagado",
            color = Color(0xFF93A1B3),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.18f), contentColor = color),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VideoSurface(url: String, token: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember {
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context).build().apply {
            val source = HlsMediaSource.Factory(dataSource)
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(url)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build(),
                )
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
