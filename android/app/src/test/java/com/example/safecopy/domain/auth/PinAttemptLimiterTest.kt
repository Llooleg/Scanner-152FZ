package com.example.safecopy.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinAttemptLimiterTest {

    private var now = 1_000L
    private val limiter = PinAttemptLimiter(maxAttempts = 5, lockoutMillis = 30_000L, clock = { now })

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
}
