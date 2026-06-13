package com.mamvid.kiosko.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mamvid.kiosko.core.utils.Logger

class KioskoWebViewClient(
    private val allowSslErrors: Boolean = false,
    private val callback: WebViewClientCallback
) : WebViewClient() {

    interface WebViewClientCallback {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onError(errorCode: Int, description: String, url: String)
        fun onHttpError(statusCode: Int, url: String)
    }

    private val tag = "WebViewClient"

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Logger.d(tag, "Page started: $url")
        callback.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        Logger.d(tag, "Page finished: $url")
        callback.onPageFinished(url)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            Logger.e(tag, "Error ${error.errorCode}: ${error.description} — ${request.url}")
            callback.onError(error.errorCode, error.description.toString(), request.url.toString())
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request.isForMainFrame) {
            Logger.w(tag, "HTTP ${errorResponse.statusCode} — ${request.url}")
            callback.onHttpError(errorResponse.statusCode, request.url.toString())
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        if (allowSslErrors) {
            Logger.w(tag, "SSL error ignored (dev mode): $error")
            handler.proceed()
        } else {
            Logger.w(tag, "SSL error cancelled: $error")
            handler.cancel()
            callback.onError(-2, "Error de certificado SSL", error.url ?: "")
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        Logger.d(tag, "Navigation: ${request.url}")
        return false
    }
}
