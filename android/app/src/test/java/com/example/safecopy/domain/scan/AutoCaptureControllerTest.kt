package com.example.safecopy.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCaptureControllerTest {

    private var now = 0L
    private val controller = AutoCaptureController(
        clock = { now },
        maxTiltDegrees = 3f,
        stableFrames = 3,
        maxCornerShift = 0.05f,
        holdMillis = 1_000L,
    )

    private val quad = Quad(
        NormPoint(0.1f, 0.1f), NormPoint(0.9f, 0.1f), NormPoint(0.9f, 0.9f), NormPoint(0.1f, 0.9f),
    )

    @Test
    fun `no document means searching`() {
        assertEquals(AutoCaptureState.Searching, controller.update(null, 0f))
    }

    @Test
    fun `quad must be stable for several frames before countdown`() {
        assertEquals(AutoCaptureState.Searching, controller.update(quad, 0f))
        assertEquals(AutoCaptureState.Searching, controller.update(quad, 0f))
        assertEquals(AutoCaptureState.Countdown(1_000L), controller.update(quad, 0f))
    }

    @Test
    fun `fires after hold time when level`() {
        repeat(3) { controller.update(quad, 1f) }
        now += 600
        assertEquals(AutoCaptureState.Countdown(400L), controller.update(quad, 1f))
        now += 400
        assertEquals(AutoCaptureState.Ready, controller.update(quad, 1f))
    }

    @Test
    fun `tilt above limit pauses and restarts countdown`() {
        repeat(3) { controller.update(quad, 0f) }
        now += 800
        assertEquals(AutoCaptureState.Tilted(5f), controller.update(quad, 5f))
        now += 100
        assertEquals(AutoCaptureState.Countdown(1_000L), controller.update(quad, 2f))
    }

    @Test
    fun `exactly three degrees is allowed`() {
        repeat(2) { controller.update(quad, 3f) }
        assertEquals(AutoCaptureState.Countdown(1_000L), controller.update(quad, 3f))
    }

    @Test
    fun `jumping quad resets stability`() {
        repeat(3) { controller.update(quad, 0f) }
        val moved = quad.copy(topLeft = NormPoint(0.3f, 0.1f))
        assertEquals(AutoCaptureState.Searching, controller.update(moved, 0f))
    }

    @Test
    fun `losing document resets countdown`() {
        repeat(3) { controller.update(quad, 0f) }
        now += 900
        controller.update(null, 0f)
        repeat(2) { controller.update(quad, 0f) }
        assertEquals(AutoCaptureState.Countdown(1_000L), controller.update(quad, 0f))
    }

    @Test
    fun `reset starts over`() {
        repeat(3) { controller.update(quad, 0f) }
        controller.reset()
        assertEquals(AutoCaptureState.Searching, controller.update(quad, 0f))
    }
}
