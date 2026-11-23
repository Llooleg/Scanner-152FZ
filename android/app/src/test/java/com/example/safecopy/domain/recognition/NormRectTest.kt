package com.example.safecopy.domain.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NormRectTest {

    private val eps = 1e-6f

    @Test
    fun `subtract hole in the middle leaves four bands covering everything else`() {
        val area = NormRect(0f, 0f, 1f, 1f)
        val hole = NormRect(0.4f, 0.4f, 0.6f, 0.6f)
        val parts = area.subtract(hole)
        assertEquals(4, parts.size)
        assertEquals(1f - hole.area, parts.sumOf { it.area.toDouble() }.toFloat(), eps)
        parts.forEach { assertFalse("Полоса $it пересекает дыру", it.intersects(hole)) }
    }

    @Test
    fun `subtract hole at the edge leaves fewer parts`() {
        val area = NormRect(0f, 0f, 1f, 0.2f)
        val hole = NormRect(0.7f, 0f, 1f, 0.2f)
        val parts = area.subtract(hole)
        assertEquals(listOf(NormRect(0f, 0f, 0.7f, 0.2f)), parts)
    }

    @Test
    fun `subtract hole outside returns the rect itself`() {
        val area = NormRect(0f, 0f, 0.5f, 0.5f)
        assertEquals(listOf(area), area.subtract(NormRect(0.6f, 0.6f, 0.9f, 0.9f)))
    }

    @Test
    fun `subtract hole larger than rect removes it`() {
        assertTrue(NormRect(0.2f, 0.2f, 0.3f, 0.3f).subtract(NormRect.FULL).isEmpty())
    }

    @Test
    fun `fraction inside`() {
        val word = NormRect(0f, 0f, 0.2f, 0.1f)
        assertEquals(0.5f, word.fractionInside(NormRect(0.1f, 0f, 1f, 1f)), eps)
        assertEquals(0f, word.fractionInside(NormRect(0.5f, 0.5f, 1f, 1f)), eps)
    }

    @Test
    fun `expanded is clamped to the page`() {
        assertEquals(NormRect(0f, 0f, 1f, 1f), NormRect(0.05f, 0.05f, 0.95f, 0.95f).expanded(0.1f, 0.1f))
    }

    @Test
    fun `intersection and union`() {
        val a = NormRect(0f, 0f, 0.5f, 0.5f)
        val b = NormRect(0.25f, 0.25f, 1f, 1f)
        assertEquals(NormRect(0.25f, 0.25f, 0.5f, 0.5f), a.intersection(b))
        assertEquals(NormRect.FULL, a.union(b))
        assertNull(a.intersection(NormRect(0.6f, 0.6f, 0.7f, 0.7f)))
    }

    @Test
    fun `from pixels`() {
        assertEquals(NormRect(0.1f, 0.25f, 0.5f, 1f), NormRect.fromPixels(100, 50, 500, 200, 1000, 200))
    }
}
