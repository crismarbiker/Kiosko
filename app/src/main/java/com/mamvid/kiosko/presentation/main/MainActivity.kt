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

    private var tapCount = 0
    private var lastTapTime = 0L
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { abrirAdmin() }

    private val tag = "MainActivity"

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    // Modo configuración: la URL aún es la predeterminada o está en blanco
    private fun enModoConfiguracion() =
        currentSettings.url.isBlank() || currentSettings.url == AppConfig.DEFAULT_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
        } catch (e: Exception) {
            Logger.e(tag, "Error al inflar layout: ${e.message}")
            finish()
            return
        }
        setContentView(binding.root)

        kioskManager = KioskManager(this)
        printHandler = PrintHandler(this)

        kioskManager.setupWindowFlags()
        try { setupWebView() } catch (e: Exception) { Logger.e(tag, "Error al iniciar WebView: ${e.message}") }
        setupAccesoAdmin()
        setupBotonConfigurar()
        setupBotonReintentar()
        setupBotonAtras()
        observarViewModel()

        Logger.i(tag, "MainActivity creada")
    }

    private fun setupWebView() {
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
                    Logger.w(tag, "Error HTTP $statusCode")
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
                    abrirPopup(resultMsg)
                    return true
                }
                override fun onClosePopupWindow() {
                    cerrarPopup()
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

    // Configura la zona oculta de acceso al panel admin
    // (5 taps rápidos o mantener 3 segundos en esquina inferior izquierda)
    private fun setupAccesoAdmin() {
        binding.adminTriggerArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressHandler.postDelayed(longPressRunnable, AppConfig.ADMIN_LONG_PRESS_DURATION_MS)
                    contarTap()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }
            }
            true
        }
    }

    private fun contarTap() {
        val ahora = System.currentTimeMillis()
        if (ahora - lastTapTime > AppConfig.ADMIN_TAP_TIMEOUT_MS) tapCount = 0
        tapCount++
        lastTapTime = ahora
        Logger.d(tag, "Tap admin: $tapCount/${AppConfig.ADMIN_TAP_COUNT}")
        if (tapCount >= AppConfig.ADMIN_TAP_COUNT) {
            tapCount = 0
            longPressHandler.removeCallbacks(longPressRunnable)
            abrirAdmin()
        }
    }

    // Botón visible en la pantalla de configuración inicial
    private fun setupBotonConfigurar() {
        binding.btnGoToConfig.setOnClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
        }
    }

    private fun setupBotonReintentar() {
        binding.btnRetry.setOnClickListener { viewModel.triggerReload() }
    }

    private fun setupBotonAtras() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (popupWebView != null) { cerrarPopup(); return }
                if (binding.webView.canGoBack()) { binding.webView.goBack(); return }
                if (!enModoConfiguracion() && currentSettings.exitProtectionEnabled) {
                    mostrarDialogoSalir()
                } else {
                    finish()
                }
            }
        })
    }

    // Acceso al panel admin:
    // - En modo configuración inicial: entra directo sin contraseña
    // - Con URL configurada: pide contraseña
    private fun abrirAdmin() {
        Logger.i(tag, "Acceso admin solicitado")
        if (enModoConfiguracion()) {
            startActivity(Intent(this, AdminActivity::class.java))
            return
        }
        val dialog = AdminAuthDialog()
        dialog.currentSettings = currentSettings
        dialog.onAuthenticated = {
            if (currentSettings.kioskModeEnabled) {
                try { kioskManager.disableKioskMode() } catch (e: Exception) { Logger.w(tag, "desactivar kiosk: ${e.message}") }
            }
            startActivity(Intent(this, AdminActivity::class.java))
        }
        dialog.show(supportFragmentManager, "admin_auth")
    }

    private fun mostrarDialogoSalir() {
        val dialog = ExitProtectionDialog()
        dialog.currentSettings = currentSettings
        dialog.onExitConfirmed = { finish() }
        dialog.show(supportFragmentManager, "exit_protection")
    }

    private fun abrirPopup(resultMsg: Message) {
        cerrarPopup()
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
            override fun onCloseWindow(window: WebView) { cerrarPopup() }
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress == 100) {
                    val url = view.url ?: return
                    if (url.endsWith(".pdf", ignoreCase = true)) printHandler.printWebView(view, "PDF")
                }
            }
        }
        popup.addJavascriptInterface(
            JavaScriptBridge(this, object : JavaScriptBridge.BridgeCallback {
                override fun onPrint() { printHandler.printWebView(popup) }
                override fun onClosePopup() { runOnUiThread { cerrarPopup() } }
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
        Logger.i(tag, "Popup abierto")
    }

    private fun cerrarPopup() {
        popupWebView?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
        popupWebView = null
        binding.popupContainer.removeAllViews()
        binding.popupContainer.gone()
        Logger.i(tag, "Popup cerrado")
    }

    private fun aplicarConfiguracion(settings: AppSettings) {
        try {
            currentSettings = settings
            requestedOrientation = when (settings.orientation) {
                ScreenOrientation.PORTRAIT  -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ScreenOrientation.AUTO      -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            if (settings.keepScreenOn) kioskManager.enableKeepScreenOn()
            else kioskManager.disableKeepScreenOn()

            if (settings.kioskModeEnabled) kioskManager.enableKioskMode()
            else kioskManager.disableKioskMode()
        } catch (e: Exception) {
            Logger.w(tag, "aplicarConfiguracion: ${e.message}")
        }
    }

    private fun cargarUrl(url: String) {
        if (url.isNotBlank()) {
            Logger.i(tag, "Cargando: $url")
            binding.webView.loadUrl(url)
        }
    }

    private fun mostrarPantallaSetup() {
        binding.setupContainer.visible()
        binding.webView.gone()
        binding.errorContainer.gone()
    }

    private fun ocultarPantallaSetup() {
        binding.setupContainer.gone()
        binding.webView.requestFocus()
    }

    private fun observarViewModel() {
        var urlCargada = ""

        lifecycleScope.launch {
            viewModel.settings.collect { settings ->
                aplicarConfiguracion(settings)
                if (enModoConfiguracion()) {
                    mostrarPantallaSetup()
                    urlCargada = ""
                } else {
                    ocultarPantallaSetup()
                    if (settings.url != urlCargada) {
                        urlCargada = settings.url
                        cargarUrl(settings.url)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                // No mostrar errores de red en modo configuración
                if (enModoConfiguracion()) return@collect

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
                    urlCargada = ""
                    cargarUrl(currentSettings.url)
                    viewModel.onReloadConsumed()
                }
            }
        }
    }

    // ── JavaScriptBridge.BridgeCallback ──────────────────────────
    override fun onPrint() {
        runOnUiThread { printHandler.printWebView(binding.webView) }
    }
    override fun onClosePopup() { runOnUiThread { cerrarPopup() } }
    override fun onRestart() {
        Logger.i(tag, "Reinicio vía JS")
        finish(); startActivity(intent)
    }
    override fun onShowToast(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    // ── Ciclo de vida ─────────────────────────────────────────────
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && currentSettings.kioskModeEnabled) {
            try { kioskManager.enableFullscreen() } catch (e: Exception) { Logger.w(tag, "onWindowFocus: ${e.message}") }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        if (currentSettings.kioskModeEnabled) {
            try { kioskManager.enableKioskMode() } catch (e: Exception) { Logger.w(tag, "onResume: ${e.message}") }
        }
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        longPressHandler.removeCallbacksAndMessages(null)
        binding.webView.apply { stopLoading(); destroy() }
        cerrarPopup()
        super.onDestroy()
        Logger.i(tag, "MainActivity destruida")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (currentSettings.kioskModeEnabled) Logger.w(tag, "Usuario intentó salir — kiosk activo")
    }
}
