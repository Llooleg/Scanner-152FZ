package com.example.safecopy.data.crypto

import com.example.safecopy.domain.auth.MasterKey
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Отдельные ключи для базы и для файлов, выведенные из мастер-ключа (HMAC-SHA256 с меткой назначения,
 * т. е. HKDF-Expand на один блок). Мастер-ключ случайный, поэтому шаг HKDF-Extract не нужен.
 * Компрометация одного производного ключа не раскрывает ни мастер-ключ, ни другой производный.
 */
object VaultKeys {
    private const val DATABASE_LABEL = "safecopy/database/v1"
    private const val FILES_LABEL = "safecopy/files/v1"

    /** 32 байта для SQLCipher. Вызывающий затирает массив после использования. */
    fun databaseKey(masterKey: MasterKey): ByteArray = derive(masterKey, DATABASE_LABEL)

    fun filesKey(masterKey: MasterKey): SecretKey {
        val raw = derive(masterKey, FILES_LABEL)
        return SecretKeySpec(raw, "AES").also { raw.fill(0) }
    }

    /** Ключ в синтаксисе SQLCipher для «сырого» ключа: x'<64 hex>'. Так SQLCipher не прогоняет его через PBKDF2. */
    fun sqlCipherRawKey(key: ByteArray): ByteArray {
        val hex = CharArray(key.size * 2)
        key.forEachIndexed { i, b ->
            hex[i * 2] = HEX[(b.toInt() shr 4) and 0xF]
            hex[i * 2 + 1] = HEX[b.toInt() and 0xF]
        }
        val result = ByteArray(hex.size + 3)
        result[0] = 'x'.code.toByte()
        result[1] = '\''.code.toByte()
        hex.forEachIndexed { i, c -> result[i + 2] = c.code.toByte() }
        result[result.lastIndex] = '\''.code.toByte()
        hex.fill('0')
        return result
    }

    private fun derive(masterKey: MasterKey, label: String): ByteArray {
        val raw = masterKey.copyBytes()
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(raw, "HmacSHA256"))
            mac.update(label.toByteArray(Charsets.US_ASCII))
            return mac.doFinal(byteArrayOf(1))
        } finally {
            raw.fill(0)
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
