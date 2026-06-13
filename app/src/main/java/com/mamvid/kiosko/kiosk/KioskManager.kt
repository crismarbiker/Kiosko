package com.mamvid.kiosko.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mamvid.kiosko.core.utils.Logger

class KioskManager(private val activity: Activity) {

    private val tag = "KioskManager"
    private var kioskActive = false

    fun enableKioskMode() {
        Logger.i(tag, "Enabling kiosk mode")
        kioskActive = true
        enableFullscreen()
        enableKeepScreenOn()
        startLockTask()
    }

    fun disableKioskMode() {
        Logger.i(tag, "Disabling kiosk mode")
        kioskActive = false
        showSystemBars()
        stopLockTask()
    }

    fun enableFullscreen() {
        try {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        } catch (e: Exception) {
            Logger.w(tag, "enableFullscreen failed: ${e.message}")
        }
    }

    fun showSystemBars() {
        try {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        } catch (e: Exception) {
            Logger.w(tag, "showSystemBars failed: ${e.message}")
        }
    }

    fun enableKeepScreenOn() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun disableKeepScreenOn() {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun setupWindowFlags() {
        activity.window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    private fun startLockTask() {
        try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                    activity.startLockTask()
                    Logger.i(tag, "Lock task started")
                }
            }
        } catch (e: Exception) {
            Logger.w(tag, "Lock task not available: ${e.message}")
        }
    }

    private fun stopLockTask() {
        try {
            activity.stopLockTask()
            Logger.i(tag, "Lock task stopped")
        } catch (e: Exception) {
            Logger.w(tag, "Stop lock task failed: ${e.message}")
        }
    }

    val isActive: Boolean get() = kioskActive
}
