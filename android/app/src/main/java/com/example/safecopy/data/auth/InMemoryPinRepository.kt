package com.example.safecopy.data.auth

import com.example.safecopy.domain.auth.PinRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ВРЕМЕННАЯ реализация для каркаса: PIN живёт только в памяти процесса,
 * после перезапуска приложение снова попросит его задать.
 * TODO(этап 1): заменить на KeystorePinRepository (см. комментарий в [PinRepository]).
 */
@Singleton
class InMemoryPinRepository @Inject constructor() : PinRepository {
    private var pin: String? = null

    override suspend fun isPinSet(): Boolean = pin != null

    override suspend fun setPin(pin: String) {
        this.pin = pin
    }

    override suspend fun verifyPin(pin: String): Boolean = this.pin == pin
}
