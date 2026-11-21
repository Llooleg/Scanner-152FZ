package com.example.safecopy.domain.auth

import javax.crypto.Cipher

/**
 * Вход по биометрии. Мастер-ключ хранится обёрнутым ключом Keystore, который система отдаёт
 * только после успешной проверки BiometricPrompt (класс Strong) и делает недействительным,
 * если на устройстве добавили новый отпечаток.
 *
 * Сценарий: получить [Cipher] → показать BiometricPrompt с CryptoObject(cipher) →
 * передать разблокированный cipher обратно в [completeEnrollment] или [unlock].
 */
interface BiometricKeyRepository {
    fun isEnrolled(): Boolean

    /**
     * Создаёт новый биометрический ключ и cipher для его привязки.
     * @throws VaultUnavailableException если на устройстве нет блокировки экрана или отпечатков.
     */
    fun createEnrollmentCipher(): Cipher

    fun completeEnrollment(authenticatedCipher: Cipher, masterKey: MasterKey)

    /** Cipher для входа или null, если ключ стал недействительным (запись о биометрии при этом стирается). */
    fun createUnlockCipher(): Cipher?

    fun unlock(authenticatedCipher: Cipher): MasterKey

    fun disable()
}

/** Есть ли на устройстве биометрия класса Strong с хотя бы одним отпечатком или лицом. */
fun interface BiometricAvailability {
    fun canUseStrongBiometric(): Boolean
}
