package com.example.safecopy.data.auth

import com.example.safecopy.data.crypto.AesGcm
import com.example.safecopy.data.crypto.SealedBox
import com.example.safecopy.domain.auth.MasterKey
import com.example.safecopy.domain.auth.PinRepository
import com.example.safecopy.domain.auth.VaultUnavailableException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

/**
 * Мастер-ключ, обёрнутый в два слоя:
 *  1. AES-GCM ключом из PIN (PBKDF2 + соль) — без PIN не развернуть;
 *  2. [deviceWrapper] (AES-GCM ключом Android Keystore) — без этого телефона не развернуть,
 *     поэтому 10 000 вариантов PIN нельзя перебрать, скопировав файл.
 *
 * Логика не зависит от Android: в unit-тестах [deviceWrapper] и [store] подменяются.
 */
class KeystorePinRepository(
    private val store: BlobStore,
    private val deviceWrapper: KeyWrapper,
    private val iterations: Int = PinKeyDerivation.DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PinRepository {

    override suspend fun prepare() = withContext(ioDispatcher) {
        deviceWrapper.warmUp()
        store.get(BLOB_NAME)?.let(::parse)
        Unit
    }

    override suspend fun isPinSet(): Boolean = withContext(ioDispatcher) { store.get(BLOB_NAME) != null }

    override suspend fun createPin(pin: String): MasterKey = withContext(ioDispatcher) {
        val masterKey = MasterKey.generate(random)
        val salt = ByteArray(PinKeyDerivation.SALT_BYTES).also(random::nextBytes)
        val pinKey = PinKeyDerivation.derive(pin, salt, iterations)
        val raw = masterKey.copyBytes()
        val inner = try {
            AesGcm.encrypt(pinKey, raw).toBytes()
        } finally {
            raw.fill(0)
        }
        val outer = deviceWrapper.wrap(inner)
        store.put(BLOB_NAME, serialize(Record(iterations, salt, outer)))
        masterKey
    }

    override suspend fun unlockWithPin(pin: String): MasterKey? = withContext(ioDispatcher) {
        val record = store.get(BLOB_NAME)?.let(::parse)
            ?: throw VaultUnavailableException("PIN не задан")
        val inner = try {
            SealedBox.fromBytes(deviceWrapper.unwrap(record.wrapped))
        } catch (e: VaultUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw VaultUnavailableException("Не удалось снять обёртку Keystore", e)
        }
        val pinKey = PinKeyDerivation.derive(pin, record.salt, record.iterations)
        try {
            MasterKey.adopt(AesGcm.decrypt(pinKey, inner))
        } catch (_: AEADBadTagException) {
            null
        }
    }

    private class Record(val iterations: Int, val salt: ByteArray, val wrapped: ByteArray)

    /** Формат: [версия, 1][итерации, 4][длина соли, 1][соль][обёрнутый ключ]. */
    private fun serialize(record: Record): ByteArray =
        ByteBuffer.allocate(1 + 4 + 1 + record.salt.size + record.wrapped.size)
            .put(FORMAT_VERSION)
            .putInt(record.iterations)
            .put(record.salt.size.toByte())
            .put(record.salt)
            .put(record.wrapped)
            .array()

    private fun parse(bytes: ByteArray): Record {
        try {
            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.get() != FORMAT_VERSION) throw VaultUnavailableException("Неизвестный формат записи PIN")
            val iterations = buffer.getInt()
            val salt = ByteArray(buffer.get().toInt() and 0xFF).also { buffer.get(it) }
            val wrapped = ByteArray(buffer.remaining()).also { buffer.get(it) }
            if (iterations <= 0 || salt.isEmpty() || wrapped.isEmpty()) {
                throw VaultUnavailableException("Повреждённая запись PIN")
            }
            return Record(iterations, salt, wrapped)
        } catch (e: java.nio.BufferUnderflowException) {
            throw VaultUnavailableException("Повреждённая запись PIN", e)
        }
    }

    private companion object {
        const val BLOB_NAME = "pin_wrapped_master_key"
        const val FORMAT_VERSION: Byte = 1
    }
}
