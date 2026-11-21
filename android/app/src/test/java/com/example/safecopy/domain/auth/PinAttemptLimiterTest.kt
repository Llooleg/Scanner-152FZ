package com.example.safecopy.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinAttemptLimiterTest {

    private class InMemoryStore : PinAttemptStore {
        var state = PinAttemptState()
        override fun load() = state
        override fun save(state: PinAttemptState) {
            this.state = state
        }
    }

    private var now = 1_000L
    private val store = InMemoryStore()
    private val limiter = newLimiter()

    private fun newLimiter() = PinAttemptLimiter(store, maxAttempts = 5, lockoutMillis = 30_000L, clock = { now })

    @Test
    fun `four failures do not lock`() {
        repeat(4) { assertFalse(limiter.registerFailure()) }
        assertFalse(limiter.isLockedOut())
    }

    @Test
    fun `fifth failure locks for 30 seconds`() {
        repeat(4) { limiter.registerFailure() }
        assertTrue(limiter.registerFailure())
        assertTrue(limiter.isLockedOut())
        assertEquals(30_000L, limiter.remainingLockoutMillis())

        now += 29_999L
        assertTrue(limiter.isLockedOut())

        now += 1L
        assertFalse(limiter.isLockedOut())
    }

    @Test
    fun `counter starts over after lockout expires`() {
        repeat(5) { limiter.registerFailure() }
        now += 30_000L
        repeat(4) { assertFalse(limiter.registerFailure()) }
        assertTrue(limiter.registerFailure())
    }

    @Test
    fun `reset clears failures`() {
        repeat(4) { limiter.registerFailure() }
        limiter.reset()
        repeat(4) { assertFalse(limiter.registerFailure()) }
    }

    @Test
    fun `failures survive restart`() {
        repeat(4) { limiter.registerFailure() }
        // Новый экземпляр над тем же хранилищем — как после перезапуска процесса.
        assertTrue(newLimiter().registerFailure())
    }

    @Test
    fun `lockout survives restart`() {
        repeat(5) { limiter.registerFailure() }
        now += 10_000L
        assertEquals(20_000L, newLimiter().remainingLockoutMillis())
    }

    @Test
    fun `clock moved back does not extend lockout beyond 30 seconds`() {
        repeat(5) { limiter.registerFailure() }
        now -= 3_600_000L
        assertEquals(30_000L, limiter.remainingLockoutMillis())
        now += 30_000L
        assertFalse(limiter.isLockedOut())
    }
}
