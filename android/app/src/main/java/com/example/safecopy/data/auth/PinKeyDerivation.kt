package com.example.safecopy.data.auth

import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Ключ из PIN: PBKDF2-HMAC-SHA256 (есть в Android с API 26). */
object PinKeyDerivation {
    const val DEFAULT_ITERATIONS = 120_000
    const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    fun derive(pin: String, salt: ByteArray, iterations: Int): SecretKey {
        val chars = pin.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(raw, "AES").also { raw.fill(0) }
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }
}
