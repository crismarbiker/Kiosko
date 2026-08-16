package com.mamvid.kiosko

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.mamvid.kiosko.core.utils.Logger

class KioskoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Kiosk siempre en modo claro — el sistema del cliente puede estar en modo oscuro
        // y el tema DayNight haría texto oscuro sobre fondo oscuro → ilegible.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        Logger.init(this)
        Logger.i("KioskoApplication", "Application started — version ${BuildConfig.VERSION_NAME}")
    }

    override fun onTerminate() {
        Logger.i("KioskoApplication", "Application terminated")
        super.onTerminate()
    }
}
