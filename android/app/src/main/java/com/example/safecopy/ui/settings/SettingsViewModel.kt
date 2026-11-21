package com.example.safecopy.ui.settings

import androidx.lifecycle.ViewModel
import com.example.safecopy.domain.auth.BiometricAvailability
import com.example.safecopy.domain.auth.BiometricKeyRepository
import com.example.safecopy.domain.auth.SessionManager
import com.example.safecopy.ui.auth.BiometricPurpose
import com.example.safecopy.ui.auth.BiometricRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.crypto.Cipher
import javax.inject.Inject

enum class SettingsMessage { BiometricUnavailable, BiometricFailed }

data class SettingsUiState(
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 1,
    val blockScreenshots: Boolean = true,
    val hideInRecents: Boolean = true,
    val biometricRequest: BiometricRequest? = null,
    val message: SettingsMessage? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val biometricKeys: BiometricKeyRepository,
    private val biometricAvailability: BiometricAvailability,
    private val sessionManager: SessionManager,
) : ViewModel() {
    // TODO(этап 8): хранить остальные настройки в DataStore и применять (FLAG_SECURE, setRecentsScreenshotEnabled, автоблокировка).
    private val _uiState = MutableStateFlow(SettingsUiState(biometricEnabled = biometricKeys.isEnrolled()))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setBiometricEnabled(enabled: Boolean) {
        if (_uiState.value.biometricRequest != null) return
        if (!enabled) {
            biometricKeys.disable()
            _uiState.update { it.copy(biometricEnabled = false) }
            return
        }
        if (!biometricAvailability.canUseStrongBiometric()) {
            _uiState.update { it.copy(message = SettingsMessage.BiometricUnavailable) }
            return
        }
        val cipher = runCatching { biometricKeys.createEnrollmentCipher() }.getOrNull()
        if (cipher == null) {
            _uiState.update { it.copy(message = SettingsMessage.BiometricUnavailable) }
            return
        }
        _uiState.update { it.copy(biometricRequest = BiometricRequest(cipher, BiometricPurpose.Enroll)) }
    }

    fun onBiometricAuthenticated(cipher: Cipher) {
        val enrolled = runCatching {
            biometricKeys.completeEnrollment(cipher, sessionManager.requireMasterKey())
        }.isSuccess
        if (!enrolled) biometricKeys.disable()
        _uiState.update {
            it.copy(
                biometricRequest = null,
                biometricEnabled = enrolled,
                message = if (enrolled) null else SettingsMessage.BiometricFailed,
            )
        }
    }

    fun onBiometricError(cancelledByUser: Boolean) {
        // Ключ уже создан, но не привязан — убираем, чтобы не висел.
        biometricKeys.disable()
        _uiState.update {
            it.copy(
                biometricRequest = null,
                biometricEnabled = false,
                message = if (cancelledByUser) null else SettingsMessage.BiometricFailed,
            )
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun setBlockScreenshots(enabled: Boolean) = _uiState.update { it.copy(blockScreenshots = enabled) }

    fun setHideInRecents(enabled: Boolean) = _uiState.update { it.copy(hideInRecents = enabled) }
}
