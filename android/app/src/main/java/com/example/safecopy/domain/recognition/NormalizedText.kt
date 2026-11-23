package com.example.safecopy.domain.recognition

/**
 * Поиск ключевых фраз в тексте OCR с допуском на ошибки распознавания.
 *
 * Текст приводится к верхнему регистру, Ё → Е, латинские буквы-двойники (A, B, C, E, H, K, M, O, P, T, X, Y)
 * заменяются кириллическими: Tesseract на русской модели нередко путает их местами.
 */
class NormalizedText private constructor(
    /** Слова после нормализации, с заменой латиницы. */
    val tokens: List<String>,
    /** Слова в верхнем регистре без замены латиницы — для латинских фраз и машиночитаемой зоны. */
    val rawTokens: List<String>,
) {
    /** Есть ли фраза: окно из того же числа слов (±1, OCR склеивает и разрывает слова) с расстоянием Левенштейна в пределах допуска. */
    fun contains(phrase: String, latin: Boolean = false): Boolean {
        val source = if (latin) rawTokens else tokens
        val target = if (latin) splitRaw(phrase).joinToString(" ") else splitNormalized(phrase).joinToString(" ")
        if (target.isEmpty() || source.isEmpty()) return false
        val words = target.count { it == ' ' } + 1
        val allowed = target.length / 6
        for (size in maxOf(1, words - 1)..words + 1) {
            for (start in 0..source.size - size) {
                val window = source.subList(start, start + size).joinToString(" ")
                if (kotlin.math.abs(window.length - target.length) > allowed) continue
                if (levenshtein(window, target, allowed) <= allowed) return true
            }
        }
        return false
    }

    companion object {
        private val LATIN_TO_CYRILLIC = mapOf(
            'A' to 'А', 'B' to 'В', 'C' to 'С', 'E' to 'Е', 'H' to 'Н', 'K' to 'К',
            'M' to 'М', 'O' to 'О', 'P' to 'Р', 'T' to 'Т', 'X' to 'Х', 'Y' to 'У',
        )
        private val SEPARATORS = Regex("[^\\p{L}\\p{Nd}]+")

        fun of(text: String) = NormalizedText(splitNormalized(text), splitRaw(text))

        private fun splitRaw(text: String): List<String> =
            text.uppercase().replace('Ё', 'Е').split(SEPARATORS).filter { it.isNotEmpty() }

        private fun splitNormalized(text: String): List<String> =
            splitRaw(text).map { token -> buildString { token.forEach { append(LATIN_TO_CYRILLIC[it] ?: it) } } }

        /** Расстояние Левенштейна; при превышении [limit] возвращает limit + 1, не досчитывая. */
        internal fun levenshtein(a: String, b: String, limit: Int): Int {
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                var rowMin = current[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                    rowMin = minOf(rowMin, current[j])
                }
                if (rowMin > limit) return limit + 1
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }
    }
}
