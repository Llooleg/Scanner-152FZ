package com.example.safecopy.domain.recognition

import com.example.safecopy.domain.model.DocumentType

/** Распознанный символ и его рамка в долях страницы. */
data class OcrSymbol(val text: String, val box: NormRect)

/** Распознанное слово. [line] — номер строки текста на странице, слова одной строки идут подряд. */
data class OcrWord(
    val text: String,
    val box: NormRect,
    val confidence: Float,
    val line: Int,
    val symbols: List<OcrSymbol>,
)

/**
 * Результат OCR одной страницы.
 * [rotationDegrees] — на сколько градусов по часовой стрелке повернуть исходную страницу, чтобы текст стал
 * горизонтальным; рамки слов даны уже в повёрнутой («прямой») странице. [aspect] — высота / ширина прямой страницы.
 */
data class PageText(
    val rotationDegrees: Int,
    val aspect: Float,
    val words: List<OcrWord>,
) {
    val text: String get() = words.joinToString(" ") { it.text }

    val meanConfidence: Float get() = if (words.isEmpty()) 0f else words.map { it.confidence }.average().toFloat()

    companion object {
        /** OCR не сработал: без поворота и без слов, поля будут взяты только из шаблона. */
        fun empty(aspect: Float) = PageText(rotationDegrees = 0, aspect = aspect, words = emptyList())
    }
}

/** Результат распознавания документа: тип (null — не определён) и текст страниц. */
data class Recognition(
    val detectedType: DocumentType?,
    val pages: List<PageText>,
)
