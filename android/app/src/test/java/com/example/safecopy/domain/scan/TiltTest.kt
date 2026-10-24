package com.example.safecopy.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TiltTest {

    private val g = 9.81f

    @Test
    fun `phone lying flat has zero tilt`() {
        val reading = Tilt.fromGravity(0f, 0f, g)
        assertEquals(0f, reading.degrees, 0.01f)
        assertEquals(0f, reading.bubbleX, 1e-6f)
        assertEquals(0f, reading.bubbleY, 1e-6f)
    }

    @Test
    fun `tilt angle matches gravity direction`() {
        val angle = Math.toRadians(3.0)
        val reading = Tilt.fromGravity((g * sin(angle)).toFloat(), 0f, (g * cos(angle)).toFloat())
        assertEquals(3f, reading.degrees, 0.01f)
    }

    @Test
    fun `face down phone is also level`() {
        assertEquals(0f, Tilt.fromGravity(0f, 0f, -g).degrees, 0.01f)
    }

    @Test
    fun `upright phone is tilted 90 degrees`() {
        assertEquals(90f, Tilt.fromGravity(0f, g, 0f).degrees, 0.01f)
    }

    @Test
    fun `bubble moves to raised edge`() {
        // Опущен правый край: проекция силы тяжести на x отрицательная, пузырёк уходит влево
        val reading = Tilt.fromGravity(-1f, 0f, g)
        assertTrue(reading.bubbleX < 0)
    }

    @Test
    fun `zero vector is treated as maximum tilt`() {
        assertEquals(90f, Tilt.fromGravity(0f, 0f, 0f).degrees, 0.01f)
    }

    @Test
    fun `low pass filter smooths jumps`() {
        val filter = LowPassFilter(alpha = 0.8f)
        filter.apply(0f, 0f, 10f)
        val smoothed = filter.apply(10f, 0f, 10f)
        assertEquals(2f, smoothed[0], 1e-5f)
        assertEquals(10f, smoothed[2], 1e-5f)
    }
}
