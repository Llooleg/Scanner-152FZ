package com.example.safecopy.domain.auth

/**
 * Хранение и проверка PIN-кода.
 *
 * На этапе 1 реализация должна:
 *  - выводить из PIN ключ (PBKDF2 + соль) и обёртывать им мастер-ключ хранилища;
 *  - дополнительно обёртывать результат ключом Android Keystore,
 *    чтобы PIN нельзя было перебрать вне устройства.
 */
interface PinRepository {
    suspend fun isPinSet(): Boolean
    suspend fun setPin(pin: String)
    suspend fun verifyPin(pin: String): Boolean
}
