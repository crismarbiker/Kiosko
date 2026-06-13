package com.mamvid.kiosko.webview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.mamvid.kiosko.core.utils.Logger

@SuppressLint("SetJavaScriptEnabled")
class KioskoWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private val tag = "KioskoWebView"

    init {
        configureSettings()
        configureCookies()
    }

    private fun configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            blockNetworkImage = false
            blockNetworkLoads = false
            @Suppress("DEPRECATION")
            saveFormData = false
        }

        isScrollbarFadingEnabled = true
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        Logger.i(tag, "WebView settings configured")
    }

    private fun configureCookies() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@KioskoWebView, true)
        }
        Logger.i(tag, "Cookies enabled")
    }

    fun clearAllData() {
        clearCache(true)
        clearHistory()
        clearFormData()
        clearSslPreferences()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebViewDatabase.getInstance(context).clearFormData()
        Logger.i(tag, "All WebView data cleared")
    }

    fun clearCacheOnly() {
        clearCache(true)
        Logger.i(tag, "WebView cache cleared")
    }

    fun persistCookies() {
        CookieManager.getInstance().flush()
    }

    override fun onDetachedFromWindow() {
        persistCookies()
        super.onDetachedFromWindow()
    }
}
