package com.mamvid.kiosko.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mamvid.kiosko.core.config.AppConfig
import com.mamvid.kiosko.core.domain.model.AppSettings
import com.mamvid.kiosko.core.domain.model.ScreenOrientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kiosko_settings")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_URL = stringPreferencesKey("url")
        val KEY_BACKUP_URL = stringPreferencesKey("backup_url")
        val KEY_PASSWORD = stringPreferencesKey("admin_password")
        val KEY_KIOSK_MODE = booleanPreferencesKey("kiosk_mode")
        val KEY_EXIT_PROTECTION = booleanPreferencesKey("exit_protection")
        val KEY_FULLSCREEN = booleanPreferencesKey("fullscreen")
        val KEY_ORIENTATION = stringPreferencesKey("orientation")
        val KEY_AUTO_RELOAD = booleanPreferencesKey("auto_reload")
        val KEY_AUTO_RELOAD_MINUTES = intPreferencesKey("auto_reload_minutes")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_ALLOW_SSL_ERRORS = booleanPreferencesKey("allow_ssl_errors")
        val KEY_SHOW_STATUS_BAR = booleanPreferencesKey("show_status_bar")
        val KEY_DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences -> preferences.toAppSettings() }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_URL] = settings.url
            prefs[KEY_BACKUP_URL] = settings.backupUrl
            prefs[KEY_PASSWORD] = settings.adminPassword
            prefs[KEY_KIOSK_MODE] = settings.kioskModeEnabled
            prefs[KEY_EXIT_PROTECTION] = settings.exitProtectionEnabled
            prefs[KEY_FULLSCREEN] = settings.fullscreenEnabled
            prefs[KEY_ORIENTATION] = settings.orientation.name
            prefs[KEY_AUTO_RELOAD] = settings.autoReloadEnabled
            prefs[KEY_AUTO_RELOAD_MINUTES] = settings.autoReloadMinutes
            prefs[KEY_KEEP_SCREEN_ON] = settings.keepScreenOn
            prefs[KEY_ALLOW_SSL_ERRORS] = settings.allowSslErrors
            prefs[KEY_SHOW_STATUS_BAR] = settings.showStatusBar
            prefs[KEY_DEVELOPER_MODE] = settings.developerModeEnabled
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private fun Preferences.toAppSettings() = AppSettings(
        url = this[KEY_URL] ?: AppConfig.DEFAULT_URL,
        backupUrl = this[KEY_BACKUP_URL] ?: AppConfig.DEFAULT_BACKUP_URL,
        adminPassword = this[KEY_PASSWORD] ?: AppConfig.DEFAULT_PASSWORD,
        kioskModeEnabled = this[KEY_KIOSK_MODE] ?: false,
        exitProtectionEnabled = this[KEY_EXIT_PROTECTION] ?: false,
        fullscreenEnabled = this[KEY_FULLSCREEN] ?: true,
        orientation = runCatching {
            ScreenOrientation.valueOf(this[KEY_ORIENTATION] ?: ScreenOrientation.LANDSCAPE.name)
        }.getOrDefault(ScreenOrientation.LANDSCAPE),
        autoReloadEnabled = this[KEY_AUTO_RELOAD] ?: false,
        autoReloadMinutes = this[KEY_AUTO_RELOAD_MINUTES] ?: AppConfig.DEFAULT_AUTO_RELOAD_MINUTES,
        keepScreenOn = this[KEY_KEEP_SCREEN_ON] ?: true,
        allowSslErrors = this[KEY_ALLOW_SSL_ERRORS] ?: false,
        showStatusBar = this[KEY_SHOW_STATUS_BAR] ?: false,
        developerModeEnabled = this[KEY_DEVELOPER_MODE] ?: false
    )
}
