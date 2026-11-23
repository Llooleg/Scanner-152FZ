package com.example.safecopy.domain.recognition

import com.example.safecopy.domain.model.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentClassifierTest {

    @Test
    fun passport() {
        val text = "РОССИЙСКАЯ ФЕДЕРАЦИЯ Паспорт выдан ОТДЕЛОМ УФМС Дата выдачи 01.02.2015 Код подразделения 770-001"
        assertEquals(DocumentType.PASSPORT_RF, DocumentClassifier.classify(text).type)
    }

    @Test
    fun `passport with OCR errors and latin lookalikes`() {
        // «О» распознано как ноль, «А» и «Е» — латиницей.
        val text = "ПACП0PT ВЫДAH Koд пoдpaздeлeния 770-001"
        assertEquals(DocumentType.PASSPORT_RF, DocumentClassifier.classify(text).type)
    }

    @Test
    fun `passport registration page`() {
        assertEquals(DocumentType.PASSPORT_RF, DocumentClassifier.classify("МЕСТО ЖИТЕЛЬСТВА Зарегистрирован г. Москва").type)
    }

    @Test
    fun `passport machine readable zone`() {
        assertEquals(DocumentType.PASSPORT_RF, DocumentClassifier.classify("PNRUSIVANOV<<IVAN<IVANOVICH<<<<<<").type)
    }

    @Test
    fun snils() {
        val text = "СТРАХОВОЕ СВИДЕТЕЛЬСТВО ОБЯЗАТЕЛЬНОГО ПЕНСИОННОГО СТРАХОВАНИЯ 123-456-789 00"
        assertEquals(DocumentType.SNILS, DocumentClassifier.classify(text).type)
    }

    @Test
    fun `driver license by latin title`() {
        assertEquals(DocumentType.DRIVER_LICENSE, DocumentClassifier.classify("ВОДИТЕЛЬСКОЕ УДОСТОВЕРЕНИЕ DRIVING LICENCE").type)
        assertEquals(DocumentType.DRIVER_LICENSE, DocumentClassifier.classify("PERMIS DE CONDUIRE 99 99 123456").type)
    }

    @Test
    fun inn() {
        val text = "СВИДЕТЕЛЬСТВО о постановке на учет физического лица в налоговом органе ИНН 771234567890"
        assertEquals(DocumentType.INN, DocumentClassifier.classify(text).type)
    }

    @Test
    fun `unrelated text is not classified`() {
        assertNull(DocumentClassifier.classify("Счёт на оплату № 15 от 12.03.2026 Итого 1500 руб").type)
        assertNull(DocumentClassifier.classify("").type)
    }

    @Test
    fun `single weak keyword is not enough`() {
        // «Фамилия» есть во многих документах.
        assertNull(DocumentClassifier.classify("Фамилия Иванов").type)
    }

    @Test
    fun `fuzzy matching tolerates one error but not garbage`() {
        val text = NormalizedText.of("КОД ПОДРАЗДЕЛЕНИЯ")
        assertTrue(text.contains("код подразделения"))
        assertTrue(NormalizedText.of("КОД ПОДРАЗДЕЛЕНИ").contains("код подразделения"))
        assertFalse(NormalizedText.of("КОТ ПОД ОДЕЯЛОМ").contains("код подразделения"))
    }

    @Test
    fun `short keyword must match exactly`() {
        assertTrue(NormalizedText.of("ИНН 7701").contains("инн"))
        assertFalse(NormalizedText.of("ИНГ 7701").contains("инн"))
    }
}
