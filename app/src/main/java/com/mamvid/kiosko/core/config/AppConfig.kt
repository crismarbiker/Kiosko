package com.mamvid.kiosko.core.config

object AppConfig {
    const val DEFAULT_URL = "https://example.com"
    const val DEFAULT_BACKUP_URL = ""
    const val DEFAULT_PASSWORD = "admin123"

    const val ADMIN_LONG_PRESS_DURATION_MS = 3000L
    const val ADMIN_TAP_COUNT = 5
    const val ADMIN_TAP_TIMEOUT_MS = 3000L

    const val RECONNECT_DELAY_MS = 5000L
    const val RECONNECT_MAX_ATTEMPTS = 20

    const val DEFAULT_AUTO_RELOAD_MINUTES = 60

    const val JS_BRIDGE_NAME = "Android"

    const val LOG_TAG = "Kiosko"
    const val MAX_LOG_FILES = 7
}
