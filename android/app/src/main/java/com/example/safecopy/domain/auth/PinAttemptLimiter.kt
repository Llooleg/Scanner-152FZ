package com.example.safecopy.domain.auth

/**
 * Ограничение попыток ввода PIN: после [maxAttempts] ошибок подряд ввод блокируется на [lockoutMillis].
 * Требование ТЗ: 5 попыток, блокировка 30 секунд.
 *
 * Состояние лежит в [store], поэтому перезапуск приложения не сбрасывает ни счётчик, ни блокировку.
 * [clock] — настенные часы: монотонные часы обнуляются при перезагрузке телефона.
 * Если часы перевели назад, блокировка не растягивается дольше [lockoutMillis].
 */
class PinAttemptLimiter(
    private val store: PinAttemptStore,
    private val maxAttempts: Int = 5,
    private val lockoutMillis: Long = 30_000L,
    private val clock: () -> Long,
) {
    @Synchronized
    fun remainingLockoutMillis(): Long {
        val state = store.load()
        val now = clock()
        val remaining = state.lockedUntilMillis - now
        if (remaining > lockoutMillis) {
            store.save(state.copy(lockedUntilMillis = now + lockoutMillis))
            return lockoutMillis
        }
        return remaining.coerceAtLeast(0L)
    }

    fun isLockedOut(): Boolean = remainingLockoutMillis() > 0

    /** Регистрирует неверный PIN. Возвращает true, если после этой попытки ввод заблокирован. */
    @Synchronized
    fun registerFailure(): Boolean {
        val failed = store.load().failedAttempts + 1
        if (failed >= maxAttempts) {
            store.save(PinAttemptState(failedAttempts = 0, lockedUntilMillis = clock() + lockoutMillis))
            return true
        }
        store.save(PinAttemptState(failedAttempts = failed))
        return false
    }

    @Synchronized
    fun reset() {
        store.save(PinAttemptState())
    }
}
