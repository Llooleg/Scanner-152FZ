package com.example.safecopy.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecopy.domain.auth.BiometricAvailability
import com.example.safecopy.domain.auth.BiometricKeyRepository
import com.example.safecopy.domain.auth.MasterKey
import com.example.safecopy.domain.auth.PinAttemptLimiter
import com.example.safecopy.domain.auth.PinRepository
import com.example.safecopy.domain.auth.SessionManager
import com.example.safecopy.ui.auth.BiometricPurpose
import com.example.safecopy.ui.auth.BiometricRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

enum class LockMode { Loading, CreatePin, ConfirmPin, EnterPin }

enum class LockError { Mismatch, WrongPin, BiometricChanged, BiometricFailed, Storage }

data class LockUiState(
    val mode: LockMode = LockMode.Loading,
    val enteredDigits: Int = 0,
    val error: LockError? = null,
    val lockoutSecondsLeft: Int = 0,
    val isBusy: Boolean = false,
    val isUnlocked: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val biometricRequest: BiometricRequest? = null,
) {
    val isInputEnabled: Boolean
        get() = mode != LockMode.Loading && lockoutSecondsLeft == 0 && !isBusy && !isUnlocked &&
            biometricRequest == null
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val pinRepository: PinRepository,
    private val biometricKeys: BiometricKeyRepository,
    private val biometricAvailability: BiometricAvailability,
    private val attemptLimiter: PinAttemptLimiter,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    // Введённые цифры не попадают в UI-состояние: экрану нужно знать только их количество.
    private val entered = StringBuilder()
    private var firstPin: String? = null
    private var lockoutJob: Job? = null

    init {
        viewModelScope.launch {
            val pinSet = runCatching { pinRepository.isPinSet() }.getOrDefault(false)
            val biometric = pinSet && biometricKeys.isEnrolled() && biometricAvailability.canUseStrongBiometric()
            _uiState.update {
                it.copy(
                    mode = if (pinSet) LockMode.EnterPin else LockMode.CreatePin,
                    isBiometricAvailable = biometric,
                )
            }
            if (attemptLimiter.isLockedOut()) {
                startLockoutCountdown()
            } else if (biometric) {
                // Как в макете: при входе сразу показываем окно отпечатка, PIN — запасной путь.
                onBiometricClick()
            }
        }
    }

    fun onDigit(digit: Int) {
        if (!_uiState.value.isInputEnabled || entered.length >= PIN_LENGTH) return
        entered.append(digit)
        _uiState.update { it.copy(enteredDigits = entered.length, error = null) }
        if (entered.length == PIN_LENGTH) submit()
    }

    fun onBackspace() {
        if (!_uiState.value.isInputEnabled || entered.isEmpty()) return
        entered.deleteCharAt(entered.lastIndex)
        _uiState.update { it.copy(enteredDigits = entered.length) }
    }

    fun onBiometricClick() {
        val state = _uiState.value
        if (!state.isBiometricAvailable || !state.isInputEnabled) return
        val cipher = runCatching { biometricKeys.createUnlockCipher() }.getOrNull()
        if (cipher == null) {
            _uiState.update { it.copy(isBiometricAvailable = false, error = LockError.BiometricChanged) }
            return
        }
        _uiState.update { it.copy(biometricRequest = BiometricRequest(cipher, BiometricPurpose.Unlock), error = null) }
    }

    fun onBiometricAuthenticated(cipher: Cipher) {
        _uiState.update { it.copy(biometricRequest = null) }
        val key = runCatching { biometricKeys.unlock(cipher) }.getOrNull()
        if (key == null) {
            _uiState.update { it.copy(error = LockError.BiometricFailed) }
            return
        }
        attemptLimiter.reset()
        unlock(key)
    }

    fun onBiometricError(cancelledByUser: Boolean) {
        _uiState.update {
            it.copy(biometricRequest = null, error = if (cancelledByUser) it.error else LockError.BiometricFailed)
        }
    }

    private fun submit() {
        val pin = entered.toString()
        entered.clear()
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                when (_uiState.value.mode) {
                    LockMode.CreatePin -> {
                        firstPin = pin
                        _uiState.update { it.copy(mode = LockMode.ConfirmPin, enteredDigits = 0) }
                    }

                    LockMode.ConfirmPin -> {
                        if (pin == firstPin) {
                            unlock(pinRepository.createPin(pin))
                        } else {
                            _uiState.update {
                                it.copy(mode = LockMode.CreatePin, enteredDigits = 0, error = LockError.Mismatch)
                            }
                        }
                        firstPin = null
                    }

                    LockMode.EnterPin -> {
                        val key = pinRepository.unlockWithPin(pin)
                        if (key != null) {
                            attemptLimiter.reset()
                            unlock(key)
                        } else {
                            val lockedOut = attemptLimiter.registerFailure()
                            _uiState.update { it.copy(enteredDigits = 0, error = LockError.WrongPin) }
                            if (lockedOut) startLockoutCountdown()
                        }
                    }

                    LockMode.Loading -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Сбой Keystore — не ошибка пользователя, попытку не засчитываем.
                _uiState.update { it.copy(enteredDigits = 0, error = LockError.Storage) }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun unlock(key: MasterKey) {
        sessionManager.unlock(key)
        _uiState.update { it.copy(isUnlocked = true) }
    }

    private fun startLockoutCountdown() {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            while (true) {
                val remaining = attemptLimiter.remainingLockoutMillis()
                _uiState.update { it.copy(lockoutSecondsLeft = ((remaining + 999) / 1000).toInt()) }
                if (remaining <= 0) break
                delay(minOf(remaining, 1_000L))
            }
            _uiState.update { it.copy(error = null) }
        }
    }

    companion object {
        const val PIN_LENGTH = 4
    }
}
