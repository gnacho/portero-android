package app.portero

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.portero.data.Settings
import app.portero.service.CallService
import app.portero.ui.CallScreen
import app.portero.ui.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()

        setContent {
            val app = application as PorteroApp
            var settings by remember { mutableStateOf<Settings?>(null) }
            var showCall by remember { mutableStateOf(intent.getBooleanExtra(EXTRA_INCOMING, false)) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                settings = app.settings.settings.first()
                if (settings?.configured == true) {
                    CallService.start(this@MainActivity)
                }
            }

            val current = settings
            MaterialTheme(colorScheme = darkScheme) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B0F14))) {
                    when {
                        current == null -> Text("Cargando…", color = Color.White)
                        !current.configured -> SettingsScreen(
                            current = current,
                            onSaved = {
                                lifecycleScope.launch {
                                    settings = app.settings.settings.first()
                                    CallService.start(this@MainActivity)
                                }
                            },
                        )
                        showCall -> {
                            val vm = remember(current) { CallViewModel(app.api, current) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            CallScreen(
                                url = current.hlsUrl(),
                                token = current.token,
                                state = state,
                                onAnswer = vm::answer,
                                onHangup = vm::hangup,
                                onReject = { vm.reject(); showCall = false },
                                onOpenDoor = vm::openDoor,
                                onToggleMic = vm::toggleMic,
                            )
                        }
                        else -> HomeScreen(
                            settings = current,
                            onOpen = { showCall = true },
                            onChangeSettings = { settings = current.copy(baseUrl = "", token = "") },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_INCOMING, false)) {
            recreate()
        }
    }

    private fun requestPermissionsIfNeeded() {
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }

    companion object {
        const val EXTRA_INCOMING = "incoming_call"
        private val darkScheme = darkColorScheme(
            primary = Color(0xFF3F8CFF),
            background = Color(0xFF0B0F14),
            surface = Color(0xFF141A22),
        )
    }
}

@androidx.compose.runtime.Composable
private fun HomeScreen(
    settings: Settings,
    onOpen: () -> Unit,
    onChangeSettings: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("Escuchando el videoportero", color = Color.White, style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
        androidx.compose.material3.Button(onClick = onOpen) { Text("Abrir cámara") }
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        androidx.compose.material3.OutlinedButton(onClick = onChangeSettings) { Text("Cambiar servidor") }
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(settings.normalizedBase(), color = Color(0xFF93A1B3), style = MaterialTheme.typography.bodySmall)
    }
}
