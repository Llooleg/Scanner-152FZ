package com.example.safecopy.data.auth

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.example.safecopy.data.crypto.AesGcm
import com.example.safecopy.data.crypto.SealedBox
import com.example.safecopy.domain.auth.BiometricKeyRepository
import com.example.safecopy.domain.auth.MasterKey
import com.example.safecopy.domain.auth.VaultUnavailableException
import java.security.GeneralSecurityException
import javax.crypto.Cipher

class KeystoreBiometricKeyRepository(
    private val store: BlobStore,
    private val keys: AndroidKeystoreKeys,
) : BiometricKeyRepository {

    override fun isEnrolled(): Boolean = store.get(BLOB_NAME) != null

    override fun createEnrollmentCipher(): Cipher = try {
        AesGcm.encryptCipher(keys.createBiometricKey())
    } catch (e: GeneralSecurityException) {
        // На API < 30 генерация падает, если на телефоне нет блокировки экрана или отпечатков.
        keys.delete(AndroidKeystoreKeys.BIOMETRIC_KEY_ALIAS)
        throw VaultUnavailableException("Не удалось создать биометрический ключ", e)
    }

    override fun completeEnrollment(authenticatedCipher: Cipher, masterKey: MasterKey) {
        val raw = masterKey.copyBytes()
        try {
            store.put(BLOB_NAME, AesGcm.seal(authenticatedCipher, raw).toBytes())
        } finally {
            raw.fill(0)
        }
    }

    override fun createUnlockCipher(): Cipher? {
        val blob = store.get(BLOB_NAME) ?: return null
        val key = keys.getKey(AndroidKeystoreKeys.BIOMETRIC_KEY_ALIAS)
        if (key == null) {
            disable()
            return null
        }
        return try {
            AesGcm.decryptCipher(key, SealedBox.fromBytes(blob).iv)
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Добавили новый отпечаток или сняли блокировку экрана: старой привязке доверять нельзя.
            disable()
            null
        }
    }

    override fun unlock(authenticatedCipher: Cipher): MasterKey {
        val blob = store.get(BLOB_NAME) ?: throw VaultUnavailableException("Вход по биометрии не настроен")
        return MasterKey.adopt(authenticatedCipher.doFinal(SealedBox.fromBytes(blob).ciphertext))
    }

    override fun disable() {
        store.remove(BLOB_NAME)
        keys.delete(AndroidKeystoreKeys.BIOMETRIC_KEY_ALIAS)
    }

    private companion object {
        const val BLOB_NAME = "biometric_wrapped_master_key"
    }
}
