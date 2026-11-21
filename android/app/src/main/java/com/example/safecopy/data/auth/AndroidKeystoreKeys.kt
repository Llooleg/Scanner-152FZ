package com.example.safecopy.data.auth

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.safecopy.data.crypto.AesGcm
import com.example.safecopy.data.crypto.SealedBox
import com.example.safecopy.domain.auth.VaultUnavailableException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Ключи приложения в Android Keystore. Материал ключей никогда не покидает Keystore. */
class AndroidKeystoreKeys {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(PROVIDER).apply { load(null) }
    }

    fun warmUp() {
        keyStore
    }

    fun getKey(alias: String): SecretKey? = keyStore.getKey(alias, null) as? SecretKey

    fun delete(alias: String) {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    /** Ключ для внешнего слоя обёртки PIN-записи. Не требует аутентификации: его защищает PIN. */
    fun getOrCreateDeviceKey(): SecretKey = getKey(DEVICE_KEY_ALIAS) ?: generate(
        aesGcmSpec(DEVICE_KEY_ALIAS).build()
    )

    /**
     * Ключ, доступный только после BiometricPrompt класса Strong, на каждое использование.
     * Становится недействительным, если на устройство добавили новый отпечаток.
     */
    fun createBiometricKey(): SecretKey {
        delete(BIOMETRIC_KEY_ALIAS)
        val spec = aesGcmSpec(BIOMETRIC_KEY_ALIAS)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        return generate(spec)
    }

    private fun aesGcmSpec(alias: String) =
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

    private fun generate(spec: KeyGenParameterSpec): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(spec)
            generateKey()
        }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        const val DEVICE_KEY_ALIAS = "safecopy.device_wrap"
        const val BIOMETRIC_KEY_ALIAS = "safecopy.biometric_wrap"
    }
}

/** [KeyWrapper] на ключе Android Keystore без аутентификации. */
class KeystoreDeviceWrapper(private val keys: AndroidKeystoreKeys) : KeyWrapper {
    override fun warmUp() = keys.warmUp()

    override fun wrap(plaintext: ByteArray): ByteArray =
        AesGcm.encrypt(keys.getOrCreateDeviceKey(), plaintext).toBytes()

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val key = keys.getKey(AndroidKeystoreKeys.DEVICE_KEY_ALIAS)
            ?: throw VaultUnavailableException("Ключ Keystore для PIN отсутствует")
        return AesGcm.decrypt(key, SealedBox.fromBytes(wrapped))
    }
}
