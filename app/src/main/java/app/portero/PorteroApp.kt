package app.portero

import android.app.Application
import app.portero.data.PorteroApi
import app.portero.data.SettingsRepository

class PorteroApp : Application() {
    lateinit var api: PorteroApi
        private set
    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        api = PorteroApi()
        settings = SettingsRepository(this)
    }
}
