package com.example.safecopy.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class QuadTest {

    @Test
    fun `fromUnordered sorts corners clockwise from top left`() {
        val tl = NormPoint(0.1f, 0.15f)
        val tr = NormPoint(0.9f, 0.1f)
        val br = NormPoint(0.85f, 0.9f)
        val bl = NormPoint(0.12f, 0.88f)

        val quad = Quad.fromUnordered(listOf(br, tl, bl, tr))

        assertEquals(Quad(tl, tr, br, bl), quad)
    }

    @Test
    fun `maxCornerShift returns largest coordinate delta`() {
        val a = Quad(NormPoint(0f, 0f), NormPoint(1f, 0f), NormPoint(1f, 1f), NormPoint(0f, 1f))
        val b = a.copy(bottomRight = NormPoint(0.9f, 0.97f))

        assertEquals(0.1f, a.maxCornerShift(b), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fromUnordered requires four points`() {
        Quad.fromUnordered(listOf(NormPoint(0f, 0f)))
    }

    @Test
    fun `toUpright maps sensor point for every rotation`() {
        // Точка в левом верхнем углу кадра сенсора 400x300 (x=40, y=30 → 0.1, 0.1)
        assertEquals(NormPoint(0.1f, 0.1f), FrameGeometry.toUpright(40f, 30f, 400, 300, 0))
        // Сенсор повёрнут на 90°: левый верх сенсора становится правым верхом экрана
        assertEquals(NormPoint(0.9f, 0.1f), FrameGeometry.toUpright(40f, 30f, 400, 300, 90))
        assertEquals(NormPoint(0.9f, 0.9f), FrameGeometry.toUpright(40f, 30f, 400, 300, 180))
        assertEquals(NormPoint(0.1f, 0.9f), FrameGeometry.toUpright(40f, 30f, 400, 300, 270))
    }
}
