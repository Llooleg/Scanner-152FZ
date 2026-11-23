package com.example.safecopy.domain.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskComposerTest {

    private val numberArea = NormRect(0.1f, 0.1f, 0.5f, 0.2f)
    private val lastDigits = NormRect(0.4f, 0.1f, 0.5f, 0.2f)
    private val mrz = NormRect(0f, 0.9f, 1f, 1f)
    private val birthplace = NormRect(0.3f, 0.6f, 0.9f, 0.7f)
    private val photo = NormRect(0f, 0.5f, 0.3f, 0.8f)

    private val page = LocatedPage(
        PageRole.PASSPORT_PERSONAL,
        mapOf(
            FieldKind.NUMBER to LocatedField(
                areas = emptyList(),
                numbers = listOf(
                    NumberOccurrence(numberArea, lastDigits, keepable = true, NumberLayout.HORIZONTAL),
                    NumberOccurrence(mrz, null, keepable = false, NumberLayout.MACHINE_READABLE),
                ),
            ),
            FieldKind.BIRTHPLACE to LocatedField(listOf(birthplace)),
            FieldKind.PHOTO to LocatedField(listOf(photo)),
        ),
    )

    private fun covered(rects: List<NormRect>, x: Float, y: Float) = rects.any { it.contains(x, y) && it.area > 0 }

    @Test
    fun `defaults hide everything except photo and keep last four digits`() {
        val settings = MaskSettings.defaults(setOf(FieldKind.NUMBER, FieldKind.BIRTHPLACE, FieldKind.PHOTO))
        val mask = MaskComposer.compose(page, settings, emptyList())
        assertTrue(covered(mask.rects, 0.2f, 0.15f))
        assertFalse("последние цифры должны остаться видны", covered(mask.rects, 0.45f, 0.15f))
        assertTrue("машиночитаемая зона закрыта всегда", covered(mask.rects, 0.5f, 0.95f))
        assertTrue(covered(mask.rects, 0.6f, 0.65f))
        assertFalse("фото по умолчанию видно", covered(mask.rects, 0.15f, 0.65f))
        assertFalse(mask.numberFallback)
    }

    @Test
    fun `full number mode covers last digits`() {
        val settings = MaskSettings(setOf(FieldKind.NUMBER), NumberMode.FULL)
        val mask = MaskComposer.compose(page, settings, emptyList())
        assertTrue(covered(mask.rects, 0.45f, 0.15f))
        assertFalse(mask.numberFallback)
    }

    @Test
    fun `missing digits fall back to full mask and report it`() {
        val noDigits = page.mapNumbers { if (it.keepable) it.copy(lastDigits = null) else it }
        val mask = MaskComposer.compose(noDigits, MaskSettings(setOf(FieldKind.NUMBER), NumberMode.KEEP_LAST_4), emptyList())
        assertTrue(covered(mask.rects, 0.45f, 0.15f))
        assertTrue(mask.numberFallback)
    }

    @Test
    fun `machine readable zone alone does not trigger fallback`() {
        val onlyMrz = page.copy(
            fields = mapOf(FieldKind.NUMBER to LocatedField(emptyList(), listOf(NumberOccurrence(mrz, null, false, NumberLayout.MACHINE_READABLE))))
        )
        val mask = MaskComposer.compose(onlyMrz, MaskSettings(setOf(FieldKind.NUMBER), NumberMode.KEEP_LAST_4), emptyList())
        assertFalse(mask.numberFallback)
        assertEquals(listOf(mrz), mask.rects)
    }

    @Test
    fun `shown fields are not masked`() {
        val mask = MaskComposer.compose(page, MaskSettings(emptySet(), NumberMode.FULL), emptyList())
        assertTrue(mask.rects.isEmpty())
    }

    @Test
    fun `manual rects are always included`() {
        val manual = NormRect(0.7f, 0.3f, 0.8f, 0.4f)
        val mask = MaskComposer.compose(page, MaskSettings(emptySet(), NumberMode.FULL), listOf(manual))
        assertEquals(listOf(manual), mask.rects)
    }

    @Test
    fun `other field over the digits still hides them`() {
        // Если поле, которое скрыто, перекрывает последние цифры, приоритет у закрашивания.
        val overlapping = page.copy(fields = page.fields + (FieldKind.BIRTHPLACE to LocatedField(listOf(NormRect(0.35f, 0.05f, 0.6f, 0.25f)))))
        val settings = MaskSettings(setOf(FieldKind.NUMBER, FieldKind.BIRTHPLACE), NumberMode.KEEP_LAST_4)
        val mask = MaskComposer.compose(overlapping, settings, emptyList())
        assertTrue(covered(mask.rects, 0.45f, 0.15f))
    }
}
