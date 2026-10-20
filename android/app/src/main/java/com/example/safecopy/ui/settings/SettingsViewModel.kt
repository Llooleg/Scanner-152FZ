package com.example.safecopy.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val biometricEnabled: Boolean = true,
    val autoLockMinutes: Int = 1,
    val blockScreenshots: Boolean = true,
    val hideInRecents: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    // TODO(этап 8): хранить в DataStore и применять (FLAG_SECURE, setRecentsScreenshotEnabled, автоблокировка).
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setBiometricEnabled(enabled: Boolean) = _uiState.update { it.copy(biometricEnabled = enabled) }

    fun setBlockScreenshots(enabled: Boolean) = _uiState.update { it.copy(blockScreenshots = enabled) }

    fun setHideInRecents(enabled: Boolean) = _uiState.update { it.copy(hideInRecents = enabled) }
}
