package com.example.safecopy.domain.auth

/**
 * PIN-код и связанный с ним мастер-ключ хранилища.
 *
 * Сам PIN нигде не хранится. Из него выводится ключ (PBKDF2 + соль), которым обёрнут [MasterKey];
 * результат дополнительно обёрнут ключом Android Keystore, поэтому перебрать PIN вне устройства нельзя.
 * Верный PIN — это PIN, которым удалось развернуть мастер-ключ.
 */
interface PinRepository {
    /** Загружает Keystore заранее, чтобы первый ввод PIN не ждал. Вызывается на Launch Screen. */
    suspend fun prepare()

    suspend fun isPinSet(): Boolean

    /** Создаёт новый мастер-ключ, заворачивает его PIN-кодом и возвращает. */
    suspend fun createPin(pin: String): MasterKey

    /**
     * Мастер-ключ, если PIN верный, иначе null.
     * @throws VaultUnavailableException если ключ Keystore потерян или данные повреждены.
     */
    suspend fun unlockWithPin(pin: String): MasterKey?
}
