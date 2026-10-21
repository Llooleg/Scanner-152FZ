package com.example.safecopy.domain.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Разблокирована ли сессия. Хранится только в памяти: после гибели процесса приложение
 * снова заблокировано, даже если навигация восстановила стек экранов.
 * TODO(этап 1): держать здесь расшифрованный мастер-ключ и стирать его в [lock].
 * TODO(этап 8): вызывать [lock] по таймеру автоблокировки, когда приложение в фоне.
 */
@Singleton
class SessionManager @Inject constructor() {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun markUnlocked() {
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
    }
}
