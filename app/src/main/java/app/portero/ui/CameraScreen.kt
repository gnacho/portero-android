package app.portero.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import app.portero.data.Device
import app.portero.data.PorteroApi
import app.portero.data.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Live view of a plain camera: HLS when the device has a stream, snapshot otherwise. */
@Composable
fun CameraScreen(
    api: PorteroApi,
    server: Server,
    device: Device,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Text(device.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        if (device.stream.isNotBlank()) {
            ZoomableBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                PlayerSurface(url = server.hlsUrl(device.stream), token = server.token)
            }
        } else {
            val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null) {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { api.snapshot(server, device.id) }.getOrNull()
                }
                value = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            val image = bitmap
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    ZoomableBox(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = device.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Text("Sin imagen", color = Color(0xFF93A1B3))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Pellizca para ampliar y arrastra para mover.",
            color = Color(0xFF93A1B3),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Arrangement.spacedBy(8.dp)
    }
}

@Composable
fun PlayerSurface(url: String, token: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = androidx.compose.runtime.remember(url, token) {
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            .setAllowCrossProtocolRedirects(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 3000, 300, 300)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            val source = HlsMediaSource.Factory(dataSource)
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(url)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .setLiveConfiguration(
                            MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(1_200).build(),
                        )
                        .build(),
                )
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }
    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
