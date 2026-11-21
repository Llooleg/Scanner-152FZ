package com.example.safecopy.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterKeyTest {

    @Test
    fun `generated key is 256 bits`() {
        assertEquals(32, MasterKey.generate().copyBytes().size)
    }

    @Test(expected = IllegalStateException::class)
    fun `wiped key cannot be read`() {
        val key = MasterKey.generate()
        key.wipe()
        key.copyBytes()
    }

    @Test
    fun `wipe zeroes adopted array`() {
        val raw = ByteArray(32) { 7 }
        MasterKey.adopt(raw).wipe()
        assertTrue(raw.all { it == 0.toByte() })
    }

    @Test
    fun `session lock wipes key`() {
        val session = SessionManager()
        val raw = ByteArray(32) { 7 }
        session.unlock(MasterKey.adopt(raw))
        assertTrue(session.isUnlocked.value)
        session.lock()
        assertFalse(session.isUnlocked.value)
        assertTrue(raw.all { it == 0.toByte() })
    }
}
