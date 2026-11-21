package com.example.safecopy.data.crypto

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Результат шифрования AES-GCM: IV и шифртекст вместе с тегом аутентификации. */
class SealedBox(val iv: ByteArray, val ciphertext: ByteArray) {

    /** Формат: [длина IV, 1 байт][IV][шифртекст]. */
    fun toBytes(): ByteArray =
        ByteBuffer.allocate(1 + iv.size + ciphertext.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()

    companion object {
        fun fromBytes(bytes: ByteArray): SealedBox {
            require(bytes.isNotEmpty()) { "Пустой SealedBox" }
            val ivSize = bytes[0].toInt() and 0xFF
            require(ivSize in 12..16 && bytes.size > 1 + ivSize) { "Повреждённый SealedBox" }
            return SealedBox(
                iv = bytes.copyOfRange(1, 1 + ivSize),
                ciphertext = bytes.copyOfRange(1 + ivSize, bytes.size),
            )
        }
    }
}

/**
 * AES-256-GCM. IV всегда генерирует сам Cipher: ключи Android Keystore не принимают IV снаружи,
 * так один код работает и с ними, и с обычными ключами.
 */
object AesGcm {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    fun encryptCipher(key: SecretKey): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }

    fun decryptCipher(key: SecretKey, iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }

    fun seal(cipher: Cipher, plaintext: ByteArray): SealedBox = SealedBox(cipher.iv, cipher.doFinal(plaintext))

    fun encrypt(key: SecretKey, plaintext: ByteArray): SealedBox = seal(encryptCipher(key), plaintext)

    /** @throws javax.crypto.AEADBadTagException если ключ не тот или данные подменены. */
    fun decrypt(key: SecretKey, box: SealedBox): ByteArray = decryptCipher(key, box.iv).doFinal(box.ciphertext)
}
