package com.example.safecopy.data.auth

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit

/** Небольшие бинарные записи (обёрнутые ключи, соль). Сами по себе бесполезны без Keystore. */
interface BlobStore {
    fun get(name: String): ByteArray?
    fun put(name: String, value: ByteArray)
    fun remove(name: String)
}

/**
 * Записи синхронно (commit), чтобы ключ не потерялся, если процесс убьют сразу после создания PIN.
 * Бэкап отключён в манифесте, файл не уезжает с устройства.
 */
class SharedPreferencesBlobStore(private val prefs: SharedPreferences) : BlobStore {
    override fun get(name: String): ByteArray? =
        prefs.getString(name, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    override fun put(name: String, value: ByteArray) =
        prefs.edit(commit = true) { putString(name, Base64.encodeToString(value, Base64.NO_WRAP)) }

    override fun remove(name: String) = prefs.edit(commit = true) { remove(name) }
}

/** Внешний слой обёртки: привязывает данные к этому устройству. */
interface KeyWrapper {
    /** Подготовка заранее (загрузка AndroidKeyStore занимает заметное время). */
    fun warmUp() {}

    fun wrap(plaintext: ByteArray): ByteArray

    /** @throws com.example.safecopy.domain.auth.VaultUnavailableException если ключа обёртки нет. */
    fun unwrap(wrapped: ByteArray): ByteArray
}
