package com.example.safecopy.data.auth

import com.example.safecopy.data.crypto.AesGcm
import com.example.safecopy.data.crypto.SealedBox
import com.example.safecopy.domain.auth.MasterKey
import com.example.safecopy.domain.auth.VaultUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class KeystorePinRepositoryTest {

    private class InMemoryBlobStore : BlobStore {
        val blobs = mutableMapOf<String, ByteArray>()
        override fun get(name: String) = blobs[name]?.copyOf()
        override fun put(name: String, value: ByteArray) {
            blobs[name] = value.copyOf()
        }
        override fun remove(name: String) {
            blobs.remove(name)
        }
    }

    /** Вместо Keystore — обычный AES-ключ в памяти. [lose] имитирует потерю ключа Keystore. */
    private class FakeDeviceWrapper : KeyWrapper {
        private var key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var lost = false
        var wrapCalls = 0

        override fun wrap(plaintext: ByteArray): ByteArray {
            wrapCalls++
            return AesGcm.encrypt(key, plaintext).toBytes()
        }

        override fun unwrap(wrapped: ByteArray): ByteArray {
            if (lost) throw VaultUnavailableException("ключа нет")
            return AesGcm.decrypt(key, SealedBox.fromBytes(wrapped))
        }

        fun lose() {
            lost = true
        }
    }

    private val store = InMemoryBlobStore()
    private val wrapper = FakeDeviceWrapper()
    private val repository = newRepository()

    private fun newRepository() =
        KeystorePinRepository(store, wrapper, iterations = 1_000, ioDispatcher = Dispatchers.Unconfined)

    private fun MasterKey.bytes() = copyBytes()

    @Test
    fun `pin is not set initially`() = runBlocking {
        assertFalse(repository.isPinSet())
    }

    @Test
    fun `correct pin returns the same master key after restart`() = runBlocking {
        val created = repository.createPin("1234").bytes()
        assertTrue(repository.isPinSet())

        val unlocked = newRepository().unlockWithPin("1234")
        assertNotNull(unlocked)
        assertArrayEquals(created, unlocked!!.bytes())
    }

    @Test
    fun `wrong pin returns null`() = runBlocking {
        repository.createPin("1234")
        assertNull(repository.unlockWithPin("1235"))
        assertNull(repository.unlockWithPin("0000"))
    }

    @Test
    fun `stored record contains neither pin nor master key in plain form`() = runBlocking {
        val key = repository.createPin("1234").bytes()
        val blob = store.blobs.values.single()
        assertFalse(blob.containsSubsequence(key))
        assertFalse(blob.containsSubsequence("1234".toByteArray()))
        assertEquals(1, wrapper.wrapCalls)
    }

    @Test
    fun `each pin creation makes a new master key`() = runBlocking {
        val first = repository.createPin("1234").bytes()
        val second = repository.createPin("1234").bytes()
        assertFalse(first.contentEquals(second))
    }

    @Test(expected = VaultUnavailableException::class)
    fun `lost keystore key is reported, not treated as wrong pin`(): Unit = runBlocking {
        repository.createPin("1234")
        wrapper.lose()
        repository.unlockWithPin("1234")
    }

    @Test(expected = VaultUnavailableException::class)
    fun `corrupted record is reported`(): Unit = runBlocking {
        store.blobs["pin_wrapped_master_key"] = byteArrayOf(1, 2, 3)
        repository.unlockWithPin("1234")
    }

    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean =
        (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }
}
