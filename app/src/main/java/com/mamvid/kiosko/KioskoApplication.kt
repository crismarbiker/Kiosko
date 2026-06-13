package com.mamvid.kiosko

import android.app.Application
import com.mamvid.kiosko.core.utils.Logger

class KioskoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        Logger.i("KioskoApplication", "Application started — version ${BuildConfig.VERSION_NAME}")
    }

    override fun onTerminate() {
        Logger.i("KioskoApplication", "Application terminated")
        super.onTerminate()
    }
}
