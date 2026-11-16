package app.portero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.portero.PorteroApp
import app.portero.data.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsScreen(
    servers: List<Server>,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    var editing by remember { mutableStateOf<Server?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (editing != null) {
        ServerEditor(
            initial = editing!!,
            onCancel = { editing = null },
            onSaved = {
                editing = null
                onChanged()
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Text(
                "Servidores",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { editing = Server() }) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir", tint = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Cada casa es un servidor: URL y token del dispositivo.",
            color = Color(0xFF93A1B3),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(servers, key = { it.baseUrl }) { server ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141A22))
                        .clickable { editing = server }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(server.name.ifBlank { server.baseUrl }, color = Color.White)
                        Text(server.baseUrl, color = Color(0xFF93A1B3), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        val app = context.applicationContext as PorteroApp
                        scope.launch {
                            app.settings.remove(server.baseUrl)
                            onChanged()
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar", tint = Color(0xFFE0574F))
                    }
                }
            }
            if (servers.isEmpty()) {
                item { Text("Ninguno todavía.", color = Color(0xFF93A1B3)) }
            }
        }
    }
}

@Composable
private fun ServerEditor(
    initial: Server,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.baseUrl) }
    var token by remember { mutableStateOf(initial.token) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Servidor", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre (Casa, Oficina…)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL del servidor") },
            placeholder = { Text("https://portero.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token del dispositivo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                status = null
                scope.launch {
                    val app = context.applicationContext as PorteroApp
                    val server = Server(name.trim(), url.trim(), token.trim())
                    status = runCatching {
                        withContext(Dispatchers.IO) { app.api.devices(server) }
                    }.fold(
                        onSuccess = { "Conectado · ${it.size} dispositivos" },
                        onFailure = { "Error: ${it.message}" },
                    )
                    if (status?.startsWith("Conectado") == true) {
                        app.settings.upsert(server)
                        onSaved()
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Comprobando…" else "Guardar y comprobar")
        }
        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xFF93A1B3), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancelar")
        }
    }
}
