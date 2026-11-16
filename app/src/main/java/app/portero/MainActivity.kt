package app.portero

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.portero.data.Device
import app.portero.data.Server
import app.portero.service.CallService
import app.portero.ui.CallScreen
import app.portero.ui.CameraScreen
import app.portero.ui.DeviceCard
import app.portero.ui.HomeScreen
import app.portero.ui.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        requestBatteryExemption()

        setContent {
            val app = application as PorteroApp
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            var servers by remember { mutableStateOf<List<Server>>(emptyList()) }
            var cards by remember { mutableStateOf<List<DeviceCard>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }
            var error by remember { mutableStateOf<String?>(null) }
            var showSettings by remember { mutableStateOf(false) }
            var callCard by remember { mutableStateOf<DeviceCard?>(null) }
            var camera by remember { mutableStateOf<DeviceCard?>(null) }
            var reload by remember { mutableStateOf(0) }

            LaunchedEffect(Unit) {
                servers = app.settings.servers.first()
                if (servers.any { it.configured }) CallService.start(context)
                val requested = intent.getStringExtra(EXTRA_SERVER)
                if (intent.getBooleanExtra(EXTRA_INCOMING, false)) {
                    val server = servers.firstOrNull { it.normalizedBase() == requested } ?: servers.firstOrNull()
                    if (server != null) {
                        callCard = DeviceCard(
                            server,
                            Device(id = "portero", name = server.name, kind = "doorbell", stream = "hikvision-doorbell"),
                        )
                    }
                }
            }

            LaunchedEffect(servers, reload) {
                loading = true
                error = null
                cards = withContext(Dispatchers.IO) {
                    servers.filter { it.configured }.flatMap { server ->
                        runCatching { app.api.devices(server).map { DeviceCard(server, it) } }
                            .onFailure { error = "${server.name.ifBlank { server.baseUrl }}: ${it.message?.lineSequence()?.first()}" }
                            .getOrDefault(emptyList())
                    }
                }
                loading = false
            }

            MaterialTheme(colorScheme = darkScheme) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B0F14))) {
                    when {
                        showSettings -> SettingsScreen(
                            servers = servers,
                            onBack = { showSettings = false },
                            onChanged = {
                                scope.launch {
                                    servers = app.settings.servers.first()
                                    reload += 1
                                    if (servers.any { it.configured }) CallService.start(context)
                                }
                            },
                        )

                        camera != null -> CameraScreen(
                            api = app.api,
                            server = camera!!.server,
                            device = camera!!.device,
                            onBack = { camera = null },
                        )

                        callCard != null -> {
                            LaunchedEffect(callCard) { dismissRing() }
                            val card = callCard!!
                            val vm = remember(card.server) { CallViewModel(app.api, card.server) }
                            val state by vm.state.collectAsStateWithLifecycle()
                            CallScreen(
                                url = card.server.hlsUrl(card.device.stream.ifBlank { "hikvision-doorbell" }),
                                token = card.server.token,
                                state = state,
                                onAnswer = { dismissRing(); vm.answer() },
                                onHangup = { dismissRing(); vm.hangup() },
                                onBack = { callCard = null },
                                onOpenDoor = vm::openDoor,
                                onToggleMic = vm::toggleMic,
                            )
                        }

                        servers.none { it.configured } -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            Text("Añade un servidor en los ajustes", color = Color.White)
                        }

                        else -> HomeScreen(
                            api = app.api,
                            cards = cards,
                            loading = loading,
                            error = error,
                            cacheDir = cacheDir,
                            onOpen = { card ->
                                if (card.device.doorbell) callCard = card else camera = card
                            },
                            onSettings = { showSettings = true },
                            onRefresh = { reload += 1 },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_INCOMING, false)) recreate()
    }

    private fun dismissRing() {
        startService(Intent(this, CallService::class.java).setAction(CallService.ACTION_DISMISS))
    }

    private fun requestBatteryExemption() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        val intent = Intent(
            AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(intent) }
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
        const val EXTRA_SERVER = "server"
        private val darkScheme = darkColorScheme(
            primary = Color(0xFF3F8CFF),
            background = Color(0xFF0B0F14),
            surface = Color(0xFF141A22),
        )
    }
}

@Suppress("unused")
private fun unusedDevice(d: Device) = d
