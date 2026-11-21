package com.example.safecopy.data.auth

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.safecopy.domain.auth.PinAttemptState
import com.example.safecopy.domain.auth.PinAttemptStore

/** Счётчик попыток PIN. Запись синхронная: убить процесс сразу после ошибки не поможет сбросить счётчик. */
class SharedPreferencesPinAttemptStore(private val prefs: SharedPreferences) : PinAttemptStore {

    override fun load() = PinAttemptState(
        failedAttempts = prefs.getInt(KEY_FAILED, 0),
        lockedUntilMillis = prefs.getLong(KEY_LOCKED_UNTIL, 0L),
    )

    override fun save(state: PinAttemptState) = prefs.edit(commit = true) {
        putInt(KEY_FAILED, state.failedAttempts)
        putLong(KEY_LOCKED_UNTIL, state.lockedUntilMillis)
    }

    private companion object {
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_until"
    }
}
