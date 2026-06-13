package com.mamvid.kiosko.presentation.main

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mamvid.kiosko.core.config.AppConfig
import com.mamvid.kiosko.core.domain.model.AppSettings
import com.mamvid.kiosko.core.domain.model.ScreenOrientation
import com.mamvid.kiosko.core.utils.Logger
import com.mamvid.kiosko.core.utils.gone
import com.mamvid.kiosko.core.utils.visible
import com.mamvid.kiosko.databinding.ActivityMainBinding
import com.mamvid.kiosko.kiosk.KioskManager
import com.mamvid.kiosko.presentation.admin.AdminActivity
import com.mamvid.kiosko.printing.PrintHandler
import com.mamvid.kiosko.webview.JavaScriptBridge
import com.mamvid.kiosko.webview.KioskoWebChromeClient
import com.mamvid.kiosko.webview.KioskoWebView
import com.mamvid.kiosko.webview.KioskoWebViewClient
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), JavaScriptBridge.BridgeCallback {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var kioskManager: KioskManager
    private lateinit var printHandler: PrintHandler

    private var currentSettings: AppSettings = AppSettings()
    private var popupWebView: KioskoWebView? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private var logoTapCount = 0
    private var lastTapTime = 0L
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { triggerAdminAccess() }

    private val tag = "MainActivity"

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        kioskManager = KioskManager(this)
        printHandler = PrintHandler(this)

        kioskManager.setupWindowFlags()
        setupWebView()
        setupHiddenAdminAccess()
        setupBackPress()
        setupRetryButton()
        observeViewModel()

        Logger.i(tag, "MainActivity created")
    }

    private fun setupWebView() {
        // Avoid `apply {}` on View to prevent `tag` resolving to View.getTag() inside nested objects
        binding.webView.webViewClient = KioskoWebViewClient(
            allowSslErrors = currentSettings.allowSslErrors,
            callback = object : KioskoWebViewClient.WebViewClientCallback {
                override fun onPageStarted(url: String) {
                    viewModel.onPageStarted(url)
                    binding.progressBar.visible()
                }
                override fun onPageFinished(url: String) {
                    viewModel.onPageLoaded(url)
                }
                override fun onError(errorCode: Int, description: String, url: String) {
                    viewModel.onPageError(errorCode, description)
                    binding.progressBar.gone()
                }
                override fun onHttpError(statusCode: Int, url: String) {
                    Logger.w(tag, "HTTP error $statusCode")
                }
            }
        )

        binding.webView.webChromeClient = KioskoWebChromeClient(
            callback = object : KioskoWebChromeClient.ChromeClientCallback {
                override fun onProgressChanged(progress: Int) {
                    binding.progressBar.progress = progress
                    if (progress == 100) binding.progressBar.gone()
                }
                override fun onCreatePopupWindow(resultMsg: Message): Boolean {
                    openPopupOverlay(resultMsg)
                    return true
                }
                override fun onClosePopupWindow() {
                    closePopupOverlay()
                }
                override fun onShowFileChooser(
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: WebChromeClient.FileChooserParams
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    filePickerLauncher.launch("*/*")
                    return true
                }
            }
        )

        binding.webView.addJavascriptInterface(
            JavaScriptBridge(this, this),
            AppConfig.JS_BRIDGE_NAME
        )
    }

    private fun setupHiddenAdminAccess() {
        binding.adminTriggerArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressHandler.postDelayed(longPressRunnable, AppConfig.ADMIN_LONG_PRESS_DURATION_MS)
                    handleTap()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
    }

    private fun handleTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > AppConfig.ADMIN_TAP_TIMEOUT_MS) logoTapCount = 0
        logoTapCount++
        lastTapTime = now
        Logger.d(tag, "Admin tap: $logoTapCount/${AppConfig.ADMIN_TAP_COUNT}")
        if (logoTapCount >= AppConfig.ADMIN_TAP_COUNT) {
            logoTapCount = 0
            longPressHandler.removeCallbacks(longPressRunnable)
            triggerAdminAccess()
        }
    }

    private fun triggerAdminAccess() {
        Logger.i(tag, "Admin access triggered")
        if (currentSettings.kioskModeEnabled) kioskManager.disableKioskMode()

        val dialog = AdminAuthDialog()
        dialog.currentSettings = currentSettings
        dialog.onAuthenticated = {
            startActivity(Intent(this, AdminActivity::class.java))
        }
        dialog.show(supportFragmentManager, "admin_auth")
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (popupWebView != null) {
                    closePopupOverlay()
                    return
                }
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                    return
                }
                if (currentSettings.exitProtectionEnabled) {
                    showExitDialog()
                } else {
                    finish()
                }
            }
        })
    }

    private fun showExitDialog() {
        val dialog = ExitProtectionDialog()
        dialog.currentSettings = currentSettings
        dialog.onExitConfirmed = { finish() }
        dialog.show(supportFragmentManager, "exit_protection")
    }

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener {
            viewModel.triggerReload()
        }
    }

    private fun openPopupOverlay(resultMsg: Message) {
        closePopupOverlay()

        val popup = KioskoWebView(this)
        popup.webViewClient = KioskoWebViewClient(
            allowSslErrors = currentSettings.allowSslErrors,
            callback = object : KioskoWebViewClient.WebViewClientCallback {
                override fun onPageStarted(url: String) {}
                override fun onPageFinished(url: String) {}
                override fun onError(errorCode: Int, description: String, url: String) {}
                override fun onHttpError(statusCode: Int, url: String) {}
            }
        )
        popup.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView) {
                closePopupOverlay()
            }
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress == 100) {
                    val url = view.url ?: return
                    if (url.endsWith(".pdf", ignoreCase = true)) {
                        printHandler.printWebView(view, "PDF")
                    }
                }
            }
        }
        popup.addJavascriptInterface(
            JavaScriptBridge(this, object : JavaScriptBridge.BridgeCallback {
                override fun onPrint() { printHandler.printWebView(popup) }
                override fun onClosePopup() { runOnUiThread { closePopupOverlay() } }
                override fun onRestart() {}
                override fun onShowToast(message: String) {
                    runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
                }
            }),
            AppConfig.JS_BRIDGE_NAME
        )

        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = popup
        resultMsg.sendToTarget()

        binding.popupContainer.addView(
            popup,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        binding.popupContainer.visible()
        popupWebView = popup

        Logger.i(tag, "Popup overlay opened")
    }

    private fun closePopupOverlay() {
        popupWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        popupWebView = null
        binding.popupContainer.removeAllViews()
        binding.popupContainer.gone()
        Logger.i(tag, "Popup overlay closed")
    }

    private fun applySettings(settings: AppSettings) {
        currentSettings = settings

        requestedOrientation = when (settings.orientation) {
            ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ScreenOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        if (settings.keepScreenOn) kioskManager.enableKeepScreenOn()
        else kioskManager.disableKeepScreenOn()

        if (settings.kioskModeEnabled) kioskManager.enableKioskMode()
        else kioskManager.disableKioskMode()
    }

    private fun loadUrl(url: String) {
        if (url.isNotBlank()) {
            Logger.i(tag, "Loading: $url")
            binding.webView.loadUrl(url)
        }
    }

    private fun observeViewModel() {
        // Track loaded URL to avoid redundant reloads when StateFlow emits the same URL twice
        // (initial placeholder value vs actual DataStore value)
        var loadedUrl = ""

        lifecycleScope.launch {
            viewModel.settings.collect { settings ->
                applySettings(settings)
                if (settings.url.isNotBlank() && settings.url != loadedUrl) {
                    loadedUrl = settings.url
                    loadUrl(settings.url)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state.showError) {
                    binding.errorContainer.visible()
                    binding.webView.gone()
                    binding.tvError.text = state.errorMessage
                    binding.tvReconnecting.text = if (state.reconnectAttempt > 0)
                        "Reconectando... (intento ${state.reconnectAttempt} de 20)"
                    else "Verificando conexión..."
                } else {
                    binding.errorContainer.gone()
                    binding.webView.visible()
                }

                if (state.shouldReload) {
                    loadedUrl = ""   // allow reload even for same URL
                    loadUrl(currentSettings.url)
                    viewModel.onReloadConsumed()
                }
            }
        }
    }

    // JavaScriptBridge.BridgeCallback
    override fun onPrint() {
        runOnUiThread { printHandler.printWebView(binding.webView) }
    }

    override fun onClosePopup() {
        runOnUiThread { closePopupOverlay() }
    }

    override fun onRestart() {
        Logger.i(tag, "Restart via JS")
        finish()
        startActivity(intent)
    }

    override fun onShowToast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && currentSettings.kioskModeEnabled) {
            kioskManager.enableFullscreen()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        if (currentSettings.kioskModeEnabled) kioskManager.enableKioskMode()
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        longPressHandler.removeCallbacksAndMessages(null)
        binding.webView.apply { stopLoading(); destroy() }
        closePopupOverlay()
        super.onDestroy()
        Logger.i(tag, "MainActivity destroyed")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (currentSettings.kioskModeEnabled) {
            Logger.w(tag, "User tried to leave — kiosk active")
        }
    }
}
