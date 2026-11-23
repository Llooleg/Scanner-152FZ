package com.example.safecopy.domain.recognition

import com.example.safecopy.domain.model.DocumentType

/**
 * Тип документа по распознанному тексту: взвешенные ключевые фразы и характерные номера.
 * Тип считается определённым, если лучший счёт не меньше [MIN_SCORE] и строго больше второго.
 */
object DocumentClassifier {

    const val MIN_SCORE = 3

    private class Keyword(val phrase: String, val weight: Int, val latin: Boolean = false)

    private class NumberPattern(val regex: Regex, val weight: Int)

    private val KEYWORDS: Map<DocumentType, List<Keyword>> = mapOf(
        DocumentType.PASSPORT_RF to listOf(
            Keyword("паспорт выдан", 3),
            Keyword("код подразделения", 3),
            Keyword("место жительства", 2),
            Keyword("зарегистрирован", 2),
            Keyword("паспорт", 1),
            Keyword("российская федерация", 1),
            Keyword("дата выдачи", 1),
            Keyword("личный код", 1),
            Keyword("фамилия", 1),
            Keyword("отчество", 1),
            Keyword("место рождения", 1),
        ),
        DocumentType.SNILS to listOf(
            Keyword("страховое свидетельство", 3),
            Keyword("обязательного пенсионного страхования", 3),
            Keyword("снилс", 3),
            Keyword("страховой номер", 2),
            Keyword("дата регистрации", 1),
        ),
        DocumentType.DRIVER_LICENSE to listOf(
            Keyword("водительское удостоверение", 4),
            Keyword("driving licence", 3, latin = true),
            Keyword("permis de conduire", 3, latin = true),
            Keyword("особые отметки", 1),
        ),
        DocumentType.INN to listOf(
            Keyword("идентификационного номера налогоплательщика", 4),
            Keyword("постановке на учет", 2),
            Keyword("налоговом органе", 2),
            Keyword("налоговая служба", 2),
            Keyword("налогоплательщика", 1),
            Keyword("инн", 2),
        ),
    )

    private val NUMBER_PATTERNS: Map<DocumentType, List<NumberPattern>> = mapOf(
        // Машиночитаемая зона паспорта РФ начинается с PNRUS.
        DocumentType.PASSPORT_RF to listOf(NumberPattern(Regex("PNRUS"), 4), NumberPattern(Regex("\\b\\d{3}\\s?-\\s?\\d{3}\\b"), 1)),
        DocumentType.SNILS to listOf(NumberPattern(Regex("\\b\\d{3}-\\d{3}-\\d{3}\\s?\\d{2}\\b"), 3)),
        DocumentType.INN to listOf(NumberPattern(Regex("\\b\\d{12}\\b"), 2)),
    )

    data class Result(val type: DocumentType?, val scores: Map<DocumentType, Int>) {
        val bestScore: Int get() = scores.values.maxOrNull() ?: 0
    }

    fun classify(text: String): Result {
        val normalized = NormalizedText.of(text)
        val upper = text.uppercase()
        val scores = DocumentType.entries.filter { it != DocumentType.OTHER }.associateWith { type ->
            val keywordScore = KEYWORDS[type].orEmpty().filter { normalized.contains(it.phrase, it.latin) }.sumOf { it.weight }
            val numberScore = NUMBER_PATTERNS[type].orEmpty().filter { it.regex.containsMatchIn(upper) }.sumOf { it.weight }
            keywordScore + numberScore
        }
        val sorted = scores.entries.sortedByDescending { it.value }
        val best = sorted.first()
        val second = sorted.getOrNull(1)?.value ?: 0
        val type = best.key.takeIf { best.value >= MIN_SCORE && best.value > second }
        return Result(type, scores)
    }

    fun classify(pages: List<PageText>): Result = classify(pages.joinToString("\n") { it.text })
}
