package com.example.safecopy.domain.auth

data class PinAttemptState(
    val failedAttempts: Int = 0,
    /** Момент окончания блокировки по настенным часам, мс. 0 — блокировки нет. */
    val lockedUntilMillis: Long = 0L,
)

/** Где хранится счётчик попыток. Должен переживать перезапуск процесса. */
interface PinAttemptStore {
    fun load(): PinAttemptState
    fun save(state: PinAttemptState)
}
