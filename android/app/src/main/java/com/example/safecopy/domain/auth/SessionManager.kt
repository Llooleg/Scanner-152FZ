package com.example.safecopy.domain.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Разблокирована ли сессия и расшифрованный мастер-ключ хранилища на время сессии.
 * Хранится только в памяти: после гибели процесса приложение снова заблокировано,
 * даже если навигация восстановила стек экранов.
 * TODO(этап 8): вызывать [lock] по таймеру автоблокировки, когда приложение в фоне.
 */
@Singleton
class SessionManager @Inject constructor() {
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var masterKey: MasterKey? = null
    private val lockListeners = CopyOnWriteArrayList<() -> Unit>()

    @Synchronized
    fun unlock(key: MasterKey) {
        masterKey?.takeIf { it !== key }?.wipe()
        masterKey = key
        _isUnlocked.value = true
    }

    /** Мастер-ключ текущей сессии. Не затирать: им владеет SessionManager. */
    @Synchronized
    fun requireMasterKey(): MasterKey = checkNotNull(masterKey) { "Сессия заблокирована" }

    /** Вызывается при блокировке до затирания ключа: здесь закрываются база и прочие ресурсы сессии. */
    fun addLockListener(listener: () -> Unit) {
        lockListeners += listener
    }

    @Synchronized
    fun lock() {
        lockListeners.forEach { it() }
        masterKey?.wipe()
        masterKey = null
        _isUnlocked.value = false
    }
}
