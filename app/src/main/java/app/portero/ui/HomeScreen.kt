package app.portero.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.portero.data.Device
import app.portero.data.PorteroApi
import app.portero.data.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DeviceCard(val server: Server, val device: Device)

@Composable
fun HomeScreen(
    api: PorteroApi,
    cards: List<DeviceCard>,
    loading: Boolean,
    error: String?,
    cacheDir: File,
    onOpen: (DeviceCard) -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Dispositivos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Servidores", tint = Color.White)
            }
        }

        if (error != null) {
            Text(
                error,
                color = Color(0xFFFFB4A8),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (loading && cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(cards, key = { it.server.normalizedBase() + "/" + it.device.id }) { card ->
                DeviceTile(
                    card = card,
                    api = api,
                    cacheDir = cacheDir,
                    onClick = { onOpen(card) },
                )
            }
            if (cards.isEmpty()) {
                item {
                    Text(
                        "No hay dispositivos. Abre los ajustes y añade un servidor.",
                        color = Color(0xFF93A1B3),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(cards.size) { onRefresh() }
}

@Composable
private fun DeviceTile(
    card: DeviceCard,
    api: PorteroApi,
    cacheDir: File,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141A22))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (card.device.doorbell) Icons.Filled.DoorFront else Icons.Filled.Videocam,
                contentDescription = null,
                tint = if (card.device.doorbell) Color(0xFF3F8CFF) else Color(0xFF2FBF71),
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(Modifier.weight(1f)) {
                Text(card.device.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(card.server.name, color = Color(0xFF93A1B3), style = MaterialTheme.typography.bodySmall)
            }
        }
        val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, card.device.id) {
            val cached = File(cacheDir, "snap-${card.server.normalizedBase().hashCode()}-${card.device.id}.jpg")
            val bytes = withContext(Dispatchers.IO) {
                runCatching { api.snapshot(card.server, card.device.id) }
                    .onSuccess { runCatching { cached.writeBytes(it) } }
                    .getOrElse { if (cached.exists()) cached.readBytes() else null }
            }
            value = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = card.device.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            } else {
                Text("Sin imagen", color = Color(0xFF93A1B3), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
