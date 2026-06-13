package com.mamvid.kiosko.presentation.admin

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mamvid.kiosko.BuildConfig
import com.mamvid.kiosko.core.domain.model.AppSettings
import com.mamvid.kiosko.core.domain.model.ScreenOrientation
import com.mamvid.kiosko.core.utils.Logger
import com.mamvid.kiosko.core.utils.NetworkUtils
import com.mamvid.kiosko.core.utils.showToast
import com.mamvid.kiosko.databinding.ActivityAdminBinding
import kotlinx.coroutines.launch

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val viewModel: AdminViewModel by viewModels()
    private val tag = "AdminActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Panel de Administración"

        setupButtons()
        observeSettings()
        populateDeviceInfo()
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            viewModel.settings.collect { settings ->
                populateFields(settings)
            }
        }
    }

    private fun populateFields(settings: AppSettings) {
        binding.etUrl.setText(settings.url)
        binding.etBackupUrl.setText(settings.backupUrl)
        binding.etPassword.setText(settings.adminPassword)
        binding.switchKioskMode.isChecked = settings.kioskModeEnabled
        binding.switchExitProtection.isChecked = settings.exitProtectionEnabled
        binding.switchFullscreen.isChecked = settings.fullscreenEnabled
        binding.switchKeepScreenOn.isChecked = settings.keepScreenOn
        binding.switchAutoReload.isChecked = settings.autoReloadEnabled
        binding.switchAllowSsl.isChecked = settings.allowSslErrors
        binding.switchDeveloperMode.isChecked = settings.developerModeEnabled
        binding.etAutoReloadMinutes.setText(settings.autoReloadMinutes.toString())
        binding.switchLandscape.isChecked = settings.orientation == ScreenOrientation.LANDSCAPE
        updateConnectionStatus()
    }

    private fun updateConnectionStatus() {
        val connected = NetworkUtils.isConnected(this)
        binding.tvConnectionStatus.text = if (connected) "Conectado" else "Sin conexión"
        binding.tvConnectionStatus.setTextColor(
            getColor(if (connected) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
    }

    private fun populateDeviceInfo() {
        binding.tvDeviceInfo.text = buildString {
            appendLine("Marca: ${Build.BRAND}")
            appendLine("Modelo: ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }.trimEnd()
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener { saveSettings() }

        binding.btnClearCache.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Limpiar Caché")
                .setMessage("¿Desea borrar el caché del WebView?")
                .setPositiveButton("Sí") { _, _ ->
                    viewModel.clearCache { showToast("Caché limpiada") }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        binding.btnClearCookies.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Limpiar Cookies")
                .setMessage("¿Desea borrar todas las cookies?")
                .setPositiveButton("Sí") { _, _ ->
                    viewModel.clearCookies { showToast("Cookies eliminadas") }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        binding.btnRestart.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reiniciar Aplicación")
                .setMessage("¿Desea reiniciar la aplicación?")
                .setPositiveButton("Reiniciar") { _, _ ->
                    restartApp()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        binding.btnResetDefaults.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Restablecer Valores")
                .setMessage("¿Desea restablecer todos los valores a los predeterminados?")
                .setPositiveButton("Restablecer") { _, _ ->
                    viewModel.resetSettings { showToast("Valores restablecidos") }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        binding.switchAutoReload.setOnCheckedChangeListener { _, checked ->
            binding.tilAutoReloadMinutes.isEnabled = checked
        }
    }

    private fun saveSettings() {
        val url = binding.etUrl.text.toString().trim()
        if (url.isBlank()) {
            binding.tilUrl.error = "La URL es requerida"
            return
        }
        binding.tilUrl.error = null

        val orientation = if (binding.switchLandscape.isChecked) ScreenOrientation.LANDSCAPE else ScreenOrientation.AUTO

        val autoReloadMinutes = binding.etAutoReloadMinutes.text.toString().toIntOrNull() ?: 60

        val settings = AppSettings(
            url = url,
            backupUrl = binding.etBackupUrl.text.toString().trim(),
            adminPassword = binding.etPassword.text.toString().takeIf { it.isNotBlank() } ?: "admin123",
            kioskModeEnabled = binding.switchKioskMode.isChecked,
            exitProtectionEnabled = binding.switchExitProtection.isChecked,
            fullscreenEnabled = binding.switchFullscreen.isChecked,
            keepScreenOn = binding.switchKeepScreenOn.isChecked,
            autoReloadEnabled = binding.switchAutoReload.isChecked,
            autoReloadMinutes = autoReloadMinutes.coerceIn(1, 1440),
            orientation = orientation,
            allowSslErrors = binding.switchAllowSsl.isChecked,
            developerModeEnabled = binding.switchDeveloperMode.isChecked
        )

        viewModel.saveSettings(settings)
        showToast("Configuración guardada")
        Logger.i(tag, "Settings saved")

        finish()
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
