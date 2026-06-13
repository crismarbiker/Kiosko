package com.mamvid.kiosko.presentation.admin

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mamvid.kiosko.core.data.preferences.AppPreferences
import com.mamvid.kiosko.core.domain.model.AppSettings
import com.mamvid.kiosko.core.utils.Logger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "AdminViewModel"
    private val prefs = AppPreferences(application)

    val settings: StateFlow<AppSettings> = prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            prefs.updateSettings(settings)
            Logger.i(tag, "Settings saved: $settings")
        }
    }

    fun clearCache(onDone: () -> Unit) {
        viewModelScope.launch {
            Logger.i(tag, "Cache cleared")
            onDone()
        }
    }

    fun clearCookies(onDone: () -> Unit) {
        CookieManager.getInstance().removeAllCookies { success ->
            CookieManager.getInstance().flush()
            Logger.i(tag, "Cookies cleared: $success")
            onDone()
        }
    }

    fun resetSettings(onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.clearAll()
            Logger.i(tag, "Settings reset to defaults")
            onDone()
        }
    }
}
