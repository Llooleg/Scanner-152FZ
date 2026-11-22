package com.example.safecopy.data.crypto

import com.example.safecopy.domain.auth.MasterKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VaultKeysTest {

    private val master = MasterKey.adopt(ByteArray(32) { it.toByte() })

    @Test
    fun `derived keys are deterministic`() {
        assertArrayEquals(VaultKeys.databaseKey(master), VaultKeys.databaseKey(master))
        assertArrayEquals(VaultKeys.filesKey(master).encoded, VaultKeys.filesKey(master).encoded)
    }

    @Test
    fun `database and files keys differ from each other and from master`() {
        val db = VaultKeys.databaseKey(master)
        val files = VaultKeys.filesKey(master).encoded
        assertEquals(32, db.size)
        assertEquals(32, files.size)
        assertFalse(db.contentEquals(files))
        assertFalse(db.contentEquals(master.copyBytes()))
    }

    @Test
    fun `different master keys give different database keys`() {
        val other = MasterKey.adopt(ByteArray(32) { (it + 1).toByte() })
        assertFalse(VaultKeys.databaseKey(master).contentEquals(VaultKeys.databaseKey(other)))
    }

    @Test
    fun `sqlcipher raw key syntax`() {
        val key = ByteArray(32) { if (it == 0) 0xAB.toByte() else 0 }
        val text = String(VaultKeys.sqlCipherRawKey(key), Charsets.US_ASCII)
        assertEquals("x'ab" + "0".repeat(62) + "'", text)
    }
}
