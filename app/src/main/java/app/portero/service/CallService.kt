package app.portero.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import app.portero.MainActivity
import app.portero.PorteroApp
import app.portero.R
import app.portero.data.CallEvent
import app.portero.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the event stream open so the phone rings when somebody presses the
 * doorbell, even with the app in the background. The connection is the only way
 * to receive the call without a push service.
 */
class CallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamJob: Job? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringing = false

    private val api get() = (application as PorteroApp).api
    private val settingsRepo get() = (application as PorteroApp).settings

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(SERVICE_NOTIFICATION_ID, serviceNotification())
        vibrator = getSystemService(Vibrator::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_DISMISS -> stopRinging()
        }
        connect()
        return START_STICKY
    }

    private fun connect() {
        streamJob?.cancel()
        streamJob = scope.launch {
            while (true) {
                val settings = settingsRepo.settings.first()
                if (!settings.configured) {
                    delay(5_000)
                    continue
                }
                try {
                    api.events(settings).collect { event -> onEvent(settings, event) }
                } catch (e: Exception) {
                    Log.w(TAG, "event stream ended: ${e.message}")
                }
                delay(2_000)
            }
        }
    }

    private fun onEvent(settings: Settings, event: CallEvent) {
        if (event.ringing) {
            startRinging(settings)
        } else if (!event.active) {
            stopRinging()
        }
    }

    private fun startRinging(settings: Settings) {
        if (ringing) return
        ringing = true

        val fullScreen = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_INCOMING, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(this, CHANNEL_CALL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Llaman a la puerta")
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Abrir portero",
                    fullScreen,
                ).build(),
            )
            .build()

        notificationManager().notify(CALL_NOTIFICATION_ID, notification)
        playRingtone()
        vibrate()
    }

    private fun playRingtone() {
        player?.release()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(this@CallService, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun vibrate() {
        val v = vibrator ?: return
        val pattern = longArrayOf(0, 700, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, 0)
        }
    }

    private fun stopRinging() {
        if (!ringing) return
        ringing = false
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator?.cancel()
        notificationManager().cancel(CALL_NOTIFICATION_ID)
    }

    private fun serviceNotification(): Notification =
        Notification.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Escuchando el videoportero")
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    2,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    private fun createChannels() {
        val manager = notificationManager()
        val call = NotificationChannel(
            CHANNEL_CALL,
            getString(R.string.channel_call),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build(),
            )
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val service = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.channel_service),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(call)
        manager.createNotificationChannel(service)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallService"
        private const val CHANNEL_CALL = "incoming_call"
        private const val CHANNEL_SERVICE = "service"
        private const val SERVICE_NOTIFICATION_ID = 10
        private const val CALL_NOTIFICATION_ID = 11

        const val ACTION_STOP = "app.portero.STOP"
        const val ACTION_DISMISS = "app.portero.DISMISS"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CallService::class.java))
        }
    }
}
