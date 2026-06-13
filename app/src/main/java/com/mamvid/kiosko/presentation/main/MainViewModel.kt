package com.mamvid.kiosko.presentation.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mamvid.kiosko.core.data.preferences.AppPreferences
import com.mamvid.kiosko.core.domain.model.AppSettings
import com.mamvid.kiosko.core.utils.Logger
import com.mamvid.kiosko.core.utils.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "MainViewModel"
    private val prefs = AppPreferences(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val settings: StateFlow<AppSettings> = prefs.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var reconnectJob: Job? = null
    private var autoReloadJob: Job? = null
    private var reconnectAttempts = 0

    init {
        observeNetwork()
        observeSettingsChanges()
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            NetworkUtils.networkStateFlow(getApplication()).collect { connected ->
                Logger.i(tag, "Network: $connected")
                _isConnected.value = connected
                if (connected) onNetworkRestored() else onNetworkLost()
            }
        }
    }

    private fun observeSettingsChanges() {
        viewModelScope.launch {
            settings.collect { s ->
                if (s.autoReloadEnabled) startAutoReload(s.autoReloadMinutes)
                else stopAutoReload()
            }
        }
    }

    private fun onNetworkLost() {
        Logger.w(tag, "Network lost")
        _uiState.update { it.copy(showError = true, errorMessage = "Sin conexión a internet") }
        startReconnect()
    }

    private fun onNetworkRestored() {
        if (reconnectAttempts > 0) {
            Logger.i(tag, "Network restored — reloading")
            _uiState.update { it.copy(showError = false, shouldReload = true) }
        }
        reconnectAttempts = 0
        reconnectJob?.cancel()
    }

    private fun startReconnect() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            while (!_isConnected.value && reconnectAttempts < 20) {
                reconnectAttempts++
                Logger.d(tag, "Reconnect attempt $reconnectAttempts")
                _uiState.update { it.copy(reconnectAttempt = reconnectAttempts) }
                delay(5000)
            }
        }
    }

    private fun startAutoReload(minutes: Int) {
        stopAutoReload()
        autoReloadJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            Logger.i(tag, "Auto reload triggered")
            _uiState.update { it.copy(shouldReload = true) }
        }
    }

    private fun stopAutoReload() {
        autoReloadJob?.cancel()
        autoReloadJob = null
    }

    fun onPageStarted(url: String) {
        _uiState.update { it.copy(isLoading = true, currentUrl = url) }
    }

    fun onPageLoaded(url: String) {
        Logger.i(tag, "Page loaded: $url")
        _uiState.update { it.copy(currentUrl = url, isLoading = false, showError = false) }
    }

    fun onPageError(errorCode: Int, description: String) {
        Logger.e(tag, "Page error $errorCode: $description")
        _uiState.update { it.copy(showError = true, errorMessage = description, isLoading = false) }
    }

    fun onReloadConsumed() {
        _uiState.update { it.copy(shouldReload = false) }
    }

    fun triggerReload() {
        _uiState.update { it.copy(shouldReload = true) }
    }
}

data class MainUiState(
    val currentUrl: String = "",
    val isLoading: Boolean = true,
    val showError: Boolean = false,
    val errorMessage: String = "",
    val shouldReload: Boolean = false,
    val reconnectAttempt: Int = 0
)
