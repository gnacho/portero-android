package app.portero.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the listener after a reboot so the phone keeps ringing. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CallService.start(context)
        }
    }
}
