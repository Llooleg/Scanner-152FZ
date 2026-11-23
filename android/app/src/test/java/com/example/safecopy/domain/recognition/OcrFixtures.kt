package com.example.safecopy.domain.recognition

/** Слово OCR с рамками символов, равномерно нарезанными по ширине слова. */
internal fun word(
    text: String,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    line: Int,
    withSymbols: Boolean = true,
    confidence: Float = 90f,
): OcrWord {
    val step = (right - left) / text.length
    val symbols = if (withSymbols) {
        text.mapIndexed { i, c -> OcrSymbol(c.toString(), NormRect(left + i * step, top, left + (i + 1) * step, bottom)) }
    } else {
        emptyList()
    }
    return OcrWord(text, NormRect(left, top, right, bottom), confidence, line, symbols)
}

/** Строка из нескольких слов, разложенных слева направо с промежутками. */
internal fun line(text: String, left: Float, top: Float, charWidth: Float, height: Float, line: Int): List<OcrWord> {
    var x = left
    return text.split(' ').filter { it.isNotEmpty() }.map { token ->
        val w = word(token, x, top, x + token.length * charWidth, top + height, line)
        x += (token.length + 1) * charWidth
        w
    }
}

internal fun page(aspect: Float, vararg words: List<OcrWord>, rotation: Int = 0) =
    PageText(rotationDegrees = rotation, aspect = aspect, words = words.toList().flatten())
