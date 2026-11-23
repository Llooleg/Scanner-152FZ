package com.example.safecopy.domain.recognition

import com.example.safecopy.domain.model.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldLocatorTest {

    private val spreadAspect = 1.41f

    private fun locateOne(type: DocumentType, page: PageText, index: Int = 0) =
        FieldLocator.locate(type, List(index) { PageText.empty(1f) } + page)[index]

    private fun NormRect.covers(other: NormRect) = other.fractionInside(this) >= 0.999f

    @Test
    fun `passport spread gets template fields`() {
        val page = page(
            spreadAspect,
            line("Паспорт выдан ОТДЕЛОМ", 0.05f, 0.06f, 0.02f, 0.02f, 0),
            line("Код подразделения 770-001", 0.45f, 0.27f, 0.012f, 0.02f, 1),
        )
        val located = locateOne(DocumentType.PASSPORT_RF, page)
        assertEquals(PageRole.PASSPORT_SPREAD, located.role)
        assertEquals(
            setOf(FieldKind.NUMBER, FieldKind.DIVISION_CODE, FieldKind.BIRTHPLACE, FieldKind.PHOTO),
            located.fields.keys,
        )
        val strips = located.fields.getValue(FieldKind.NUMBER).numbers.filter { it.layout == NumberLayout.VERTICAL_STRIP }
        assertEquals(2, strips.size)
        assertTrue(strips.all { it.keepable && it.lastDigits == null })
    }

    @Test
    fun `division code found outside template is masked too`() {
        // Код подразделения сместился (неточная обрезка) и вышел за шаблон.
        val code = line("770-001", 0.05f, 0.40f, 0.012f, 0.02f, 0)
        val page = page(spreadAspect, line("Паспорт выдан", 0.05f, 0.06f, 0.02f, 0.02f, 1), code)
        val areas = locateOne(DocumentType.PASSPORT_RF, page).fields.getValue(FieldKind.DIVISION_CODE).areas
        val codeBox = code.single().box
        assertTrue("Код подразделения не закрыт", areas.any { it.covers(codeBox) })
    }

    @Test
    fun `word straddling template border is covered entirely`() {
        // Слово места рождения выходит за правую границу шаблона, но большей частью внутри.
        val word = word("МОСКОВСКАЯ", 0.70f, 0.80f, 0.92f, 0.82f, line = 0)
        val page = page(spreadAspect, listOf(word))
        val areas = locateOne(DocumentType.PASSPORT_RF, page).fields.getValue(FieldKind.BIRTHPLACE).areas
        assertTrue(areas.any { it.covers(word.box) })
    }

    @Test
    fun `horizontal passport number gets last four digits`() {
        val number = line("45 06 123456", 0.1f, 0.02f, 0.01f, 0.015f, 0)
        val page = page(0.7f, line("Код подразделения", 0.1f, 0.5f, 0.01f, 0.02f, 1), number)
        val numbers = locateOne(DocumentType.PASSPORT_RF, page).fields.getValue(FieldKind.NUMBER).numbers
        val horizontal = numbers.single { it.layout == NumberLayout.HORIZONTAL }
        assertNotNull(horizontal.lastDigits)
        val digits = horizontal.lastDigits!!
        val sixDigitWord = number[2].box
        // «3456» — правые 4 из 6 символов слова «123456».
        assertEquals(sixDigitWord.left + sixDigitWord.width * 2 / 6, digits.left, 1e-5f)
        assertEquals(sixDigitWord.right, digits.right, 1e-5f)
        assertTrue(horizontal.area.covers(number[0].box))
    }

    @Test
    fun `without symbol boxes last digits are not offered`() {
        val number = listOf(word("4506123456", 0.1f, 0.02f, 0.4f, 0.04f, line = 0, withSymbols = false))
        val page = page(0.7f, line("Код подразделения", 0.1f, 0.5f, 0.01f, 0.02f, 1), number)
        val horizontal = locateOne(DocumentType.PASSPORT_RF, page).fields.getValue(FieldKind.NUMBER).numbers
            .single { it.layout == NumberLayout.HORIZONTAL }
        assertNull(horizontal.lastDigits)
    }

    @Test
    fun `machine readable zone is found and never keepable`() {
        val mrz = line("PNRUSIVANOV<<IVAN<<<<<<<<", 0.02f, 0.40f, 0.03f, 0.03f, 0)
        val page = page(0.7f, line("Фамилия Иванов", 0.4f, 0.1f, 0.02f, 0.03f, 1), mrz)
        val located = locateOne(DocumentType.PASSPORT_RF, page)
        val mrzNumbers = located.fields.getValue(FieldKind.NUMBER).numbers.filter { it.layout == NumberLayout.MACHINE_READABLE }
        assertTrue(mrzNumbers.isNotEmpty())
        assertTrue(mrzNumbers.none { it.keepable })
        assertTrue(mrzNumbers.any { it.area.covers(mrz.single().box) })
        // Код подразделения зашифрован в той же зоне.
        assertTrue(located.fields.getValue(FieldKind.DIVISION_CODE).areas.any { it.covers(mrz.single().box) })
    }

    @Test
    fun `registration page masks residence`() {
        val page = page(0.7f, line("МЕСТО ЖИТЕЛЬСТВА", 0.3f, 0.02f, 0.02f, 0.03f, 0))
        val located = locateOne(DocumentType.PASSPORT_RF, page, index = 1)
        assertEquals(PageRole.PASSPORT_REGISTRATION, located.role)
        val residence = located.fields.getValue(FieldKind.RESIDENCE).areas.single()
        assertTrue(residence.covers(NormRect(0f, 0.1f, 1f, 1f)))
    }

    @Test
    fun `unread second passport page is treated as registration`() {
        val located = locateOne(DocumentType.PASSPORT_RF, PageText.empty(0.7f), index = 1)
        assertEquals(PageRole.PASSPORT_REGISTRATION, located.role)
    }

    @Test
    fun `unread landscape first page uses both page templates`() {
        val located = locateOne(DocumentType.PASSPORT_RF, PageText.empty(0.7f), index = 0)
        assertEquals(PageRole.PASSPORT_PAGE_UNKNOWN, located.role)
        assertTrue(located.fields.keys.containsAll(listOf(FieldKind.DIVISION_CODE, FieldKind.BIRTHPLACE, FieldKind.PHOTO)))
    }

    @Test
    fun `snils number inside template keeps last digits`() {
        val number = line("123-456-789 00", 0.25f, 0.18f, 0.03f, 0.06f, 0)
        val page = page(0.63f, line("СТРАХОВОЕ СВИДЕТЕЛЬСТВО", 0.1f, 0.02f, 0.03f, 0.05f, 1), number)
        val numbers = locateOne(DocumentType.SNILS, page).fields.getValue(FieldKind.NUMBER).numbers
        val only = numbers.single()
        assertNotNull(only.lastDigits)
        // Последние 4 цифры — «89» из «789» и «00»: рамка начинается на предпоследнем символе первого слова.
        val first = number[0].symbols
        assertEquals(first[first.size - 2].box.left, only.lastDigits!!.left, 1e-5f)
        assertEquals(number[1].box.right, only.lastDigits!!.right, 1e-5f)
        assertTrue(only.area.covers(number[1].box))
    }

    @Test
    fun `other document has no fields`() {
        val located = locateOne(DocumentType.OTHER, page(1.4f, line("ЧТО-ТО 12 34 567890", 0.1f, 0.1f, 0.02f, 0.03f, 0)))
        assertTrue(located.fields.isEmpty())
    }

    @Test
    fun `driver license front`() {
        val page = page(0.63f, line("ВОДИТЕЛЬСКОЕ УДОСТОВЕРЕНИЕ", 0.3f, 0.03f, 0.02f, 0.05f, 0))
        val located = locateOne(DocumentType.DRIVER_LICENSE, page)
        assertEquals(PageRole.DRIVER_FRONT, located.role)
        assertEquals(setOf(FieldKind.NUMBER, FieldKind.BIRTHPLACE, FieldKind.RESIDENCE, FieldKind.PHOTO), located.fields.keys)
        // Номер не распознан: последних цифр нет, при «оставить 4» номер будет закрашен целиком.
        assertNull(located.fields.getValue(FieldKind.NUMBER).numbers.single().lastDigits)
    }
}
