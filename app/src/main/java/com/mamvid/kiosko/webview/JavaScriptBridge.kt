package com.mamvid.kiosko.webview

import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import com.mamvid.kiosko.BuildConfig
import com.mamvid.kiosko.core.utils.Logger

class JavaScriptBridge(
    private val context: Context,
    private val callback: BridgeCallback
) {

    interface BridgeCallback {
        fun onPrint()
        fun onClosePopup()
        fun onRestartApp()
        fun onShowToast(message: String)
    }

    @JavascriptInterface
    fun print() {
        Logger.i("JSBridge", "print() invoked")
        callback.onPrint()
    }

    @JavascriptInterface
    fun closePopup() {
        Logger.i("JSBridge", "closePopup() invoked")
        callback.onClosePopup()
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Logger.i("JSBridge", "showToast('$message')")
        callback.onShowToast(message)
    }

    @JavascriptInterface
    fun getVersion(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE

    @JavascriptInterface
    fun getDeviceInfo(): String {
        return buildString {
            append("{")
            append("\"brand\":\"${Build.BRAND}\",")
            append("\"model\":\"${Build.MODEL}\",")
            append("\"manufacturer\":\"${Build.MANUFACTURER}\",")
            append("\"os\":\"${Build.VERSION.RELEASE}\",")
            append("\"sdk\":${Build.VERSION.SDK_INT},")
            append("\"appVersion\":\"${BuildConfig.VERSION_NAME}\"")
            append("}")
        }
    }

    @JavascriptInterface
    fun restart() {
        Logger.i("JSBridge", "restart() invoked")
        callback.onRestartApp()
    }

    @JavascriptInterface
    fun log(message: String) {
        Logger.d("JSBridge/Web", message)
    }

    @JavascriptInterface
    fun isKiosko(): Boolean = true
}
