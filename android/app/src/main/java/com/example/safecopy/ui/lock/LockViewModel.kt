package com.example.safecopy.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecopy.domain.auth.PinAttemptLimiter
import com.example.safecopy.domain.auth.PinRepository
import com.example.safecopy.domain.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LockMode { Loading, CreatePin, ConfirmPin, EnterPin }

enum class LockError { Mismatch, WrongPin }

data class LockUiState(
    val mode: LockMode = LockMode.Loading,
    val enteredDigits: Int = 0,
    val error: LockError? = null,
    val lockoutSecondsLeft: Int = 0,
    val isBusy: Boolean = false,
    val isUnlocked: Boolean = false,
) {
    val isInputEnabled: Boolean
        get() = mode != LockMode.Loading && lockoutSecondsLeft == 0 && !isBusy && !isUnlocked
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val pinRepository: PinRepository,
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
            val mode = if (pinRepository.isPinSet()) LockMode.EnterPin else LockMode.CreatePin
            _uiState.update { it.copy(mode = mode) }
            if (attemptLimiter.isLockedOut()) startLockoutCountdown()
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

    private fun submit() {
        val pin = entered.toString()
        entered.clear()
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (_uiState.value.mode) {
                LockMode.CreatePin -> {
                    firstPin = pin
                    _uiState.update { it.copy(mode = LockMode.ConfirmPin, enteredDigits = 0) }
                }

                LockMode.ConfirmPin -> {
                    if (pin == firstPin) {
                        pinRepository.setPin(pin)
                        unlock()
                    } else {
                        _uiState.update {
                            it.copy(mode = LockMode.CreatePin, enteredDigits = 0, error = LockError.Mismatch)
                        }
                    }
                    firstPin = null
                }

                LockMode.EnterPin -> {
                    if (pinRepository.verifyPin(pin)) {
                        attemptLimiter.reset()
                        unlock()
                    } else {
                        val lockedOut = attemptLimiter.registerFailure()
                        _uiState.update { it.copy(enteredDigits = 0, error = LockError.WrongPin) }
                        if (lockedOut) startLockoutCountdown()
                    }
                }

                LockMode.Loading -> Unit
            }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private fun unlock() {
        sessionManager.markUnlocked()
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
