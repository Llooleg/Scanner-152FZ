package com.example.safecopy.domain.auth

import java.security.SecureRandom

/**
 * Мастер-ключ хранилища (256 бит). Им на этапе 2 шифруются база (SQLCipher) и файлы документов.
 *
 * На диске ключ лежит только в обёрнутом виде (см. [PinRepository], [BiometricKeyRepository]).
 * В памяти живёт, пока сессия разблокирована, и затирается нулями в [wipe].
 */
class MasterKey private constructor(private val bytes: ByteArray) {

    @Volatile
    private var isWiped = false

    init {
        require(bytes.size == SIZE_BYTES) { "Мастер-ключ должен быть $SIZE_BYTES байт" }
    }

    /** Копия байтов ключа. Вызывающий обязан затереть её после использования. */
    fun copyBytes(): ByteArray {
        check(!isWiped) { "Мастер-ключ уже стёрт" }
        return bytes.copyOf()
    }

    fun wipe() {
        bytes.fill(0)
        isWiped = true
    }

    companion object {
        const val SIZE_BYTES = 32

        fun generate(random: SecureRandom = SecureRandom()): MasterKey =
            MasterKey(ByteArray(SIZE_BYTES).also(random::nextBytes))

        /** Забирает массив во владение: снаружи его больше трогать нельзя. */
        fun adopt(bytes: ByteArray): MasterKey = MasterKey(bytes)
    }
}
