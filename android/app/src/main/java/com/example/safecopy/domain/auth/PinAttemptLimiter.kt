package com.example.safecopy.domain.auth

/**
 * Ограничение попыток ввода PIN: после [maxAttempts] ошибок подряд ввод блокируется на [lockoutMillis].
 * Требование ТЗ: 5 попыток, блокировка 30 секунд.
 *
 * Время берётся из [clock], чтобы логику можно было проверить unit-тестами.
 * TODO(этап 1): сохранять счётчик и момент блокировки персистентно, иначе перезапуск приложения сбрасывает блокировку.
 */
class PinAttemptLimiter(
    private val maxAttempts: Int = 5,
    private val lockoutMillis: Long = 30_000L,
    private val clock: () -> Long,
) {
    private var failedAttempts = 0
    private var lockedUntil = 0L

    fun remainingLockoutMillis(): Long = (lockedUntil - clock()).coerceAtLeast(0L)

    fun isLockedOut(): Boolean = remainingLockoutMillis() > 0

    /** Регистрирует неверный PIN. Возвращает true, если после этой попытки ввод заблокирован. */
    fun registerFailure(): Boolean {
        failedAttempts++
        if (failedAttempts >= maxAttempts) {
            failedAttempts = 0
            lockedUntil = clock() + lockoutMillis
            return true
        }
        return false
    }

    fun reset() {
        failedAttempts = 0
        lockedUntil = 0L
    }
}
