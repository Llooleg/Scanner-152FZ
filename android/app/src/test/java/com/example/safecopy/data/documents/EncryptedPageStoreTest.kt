package com.example.safecopy.data.documents

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class EncryptedPageStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private var key = newKey()
    private val dir by lazy { File(temp.root, "pages") }
    private val store by lazy { EncryptedPageStore(dir) { key } }

    private val jpegLike = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(500) { it.toByte() }

    @Test
    fun `round trip`() {
        val name = store.write(jpegLike)
        assertArrayEquals(jpegLike, store.read(name))
    }

    @Test
    fun `file on disk is not plaintext`() {
        val name = store.write(jpegLike)
        val onDisk = File(dir, name).readBytes()
        assertFalse(onDisk.copyOfRange(0, 3).contentEquals(jpegLike.copyOfRange(0, 3)))
        assertFalse(onDisk.toList().windowed(16).contains(jpegLike.copyOfRange(100, 116).toList()))
    }

    @Test(expected = AEADBadTagException::class)
    fun `wrong key fails`() {
        val name = store.write(jpegLike)
        key = newKey()
        store.read(name)
    }

    @Test(expected = AEADBadTagException::class)
    fun `swapped file is detected`() {
        val a = store.write(jpegLike)
        val b = store.write(byteArrayOf(1, 2, 3))
        File(dir, b).copyTo(File(dir, a), overwrite = true)
        store.read(a)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `path traversal is rejected`() {
        store.read("../secrets")
    }

    @Test
    fun `orphans and temp files are removed`() {
        val kept = store.write(jpegLike)
        store.write(jpegLike)
        File(dir, "leftover.page.tmp").writeBytes(byteArrayOf(1))
        store.deleteAllExcept(setOf(kept))
        assertEquals(listOf(kept), dir.list()!!.toList())
    }
}
