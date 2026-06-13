package com.mamvid.kiosko.core.domain.model

data class AppSettings(
    val url: String = "https://example.com",
    val backupUrl: String = "",
    val adminPassword: String = "admin123",
    val kioskModeEnabled: Boolean = true,
    val exitProtectionEnabled: Boolean = true,
    val fullscreenEnabled: Boolean = true,
    val orientation: ScreenOrientation = ScreenOrientation.AUTO,
    val autoReloadEnabled: Boolean = false,
    val autoReloadMinutes: Int = 60,
    val keepScreenOn: Boolean = true,
    val allowSslErrors: Boolean = false,
    val showStatusBar: Boolean = false,
    val developerModeEnabled: Boolean = false
)

enum class ScreenOrientation(val label: String) {
    AUTO("Automática"),
    PORTRAIT("Vertical"),
    LANDSCAPE("Horizontal")
}
