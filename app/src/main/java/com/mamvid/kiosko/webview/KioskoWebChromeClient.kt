package com.mamvid.kiosko.webview

import android.net.Uri
import android.os.Message
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.JsPromptResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.mamvid.kiosko.core.utils.Logger

class KioskoWebChromeClient(
    private val callback: ChromeClientCallback
) : WebChromeClient() {

    interface ChromeClientCallback {
        fun onProgressChanged(progress: Int)
        fun onCreatePopupWindow(resultMsg: Message): Boolean
        fun onClosePopupWindow()
        fun onShowFileChooser(
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean
    }

    private val tag = "ChromeClient"

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        callback.onProgressChanged(newProgress)
    }

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
        Logger.d(tag, "JS Alert: $message")
        result.confirm()
        return true
    }

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
        Logger.d(tag, "JS Confirm: $message")
        result.confirm()
        return true
    }

    override fun onJsPrompt(
        view: WebView, url: String, message: String,
        defaultValue: String?, result: JsPromptResult
    ): Boolean {
        Logger.d(tag, "JS Prompt: $message")
        result.confirm(defaultValue)
        return true
    }

    override fun onCreateWindow(
        view: WebView, isDialog: Boolean,
        isUserGesture: Boolean, resultMsg: Message?
    ): Boolean {
        Logger.i(tag, "onCreateWindow dialog=$isDialog gesture=$isUserGesture")
        resultMsg ?: return false
        return callback.onCreatePopupWindow(resultMsg)
    }

    override fun onCloseWindow(window: WebView) {
        Logger.i(tag, "onCloseWindow")
        callback.onClosePopupWindow()
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean = callback.onShowFileChooser(filePathCallback, fileChooserParams)

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        val msg = "[${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}] ${consoleMessage.message()}"
        when (consoleMessage.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> Logger.e("JS", msg)
            ConsoleMessage.MessageLevel.WARNING -> Logger.w("JS", msg)
            else -> Logger.d("JS", msg)
        }
        return true
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        callback.invoke(origin, true, false)
    }
}
