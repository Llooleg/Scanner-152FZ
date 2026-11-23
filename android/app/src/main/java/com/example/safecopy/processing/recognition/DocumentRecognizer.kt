package com.example.safecopy.processing.recognition

import android.graphics.Bitmap
import com.example.safecopy.domain.recognition.DocumentClassifier
import com.example.safecopy.domain.recognition.PageText
import com.example.safecopy.domain.recognition.Recognition
import com.example.safecopy.domain.recognition.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Распознаёт страницы и определяет тип документа.
 *
 * Ориентация страницы заранее не известна (страницу могли снять боком), поэтому OCR пробует повороты
 * 0°, 90°, 270°, 180°. Ключевых слов мало: Tesseract умеет читать и вертикальные строки, так что слова
 * находятся и на странице, лежащей боком. Поэтому поворот принимается, только если текст на нём
 * горизонтальный — у большинства слов рамка шире, чем выше. Иначе шаблоны полей легли бы мимо.
 */
class DocumentRecognizer @Inject constructor(
    private val textRecognizer: TextRecognizer,
) {
    suspend fun recognize(pages: List<Bitmap>): Recognition {
        val texts = pages.map { analyzePage(it) }
        return Recognition(DocumentClassifier.classify(texts).type, texts)
    }

    private class Candidate(val text: PageText, val score: Int, val horizontal: Float) {
        val isUpright: Boolean get() = horizontal >= MIN_HORIZONTAL_SHARE
        val isConfident: Boolean get() = isUpright && score >= DocumentClassifier.MIN_SCORE
    }

    private val candidateOrder = compareBy<Candidate>({ it.isUpright }, { it.score }, { it.text.meanConfidence })

    private suspend fun analyzePage(page: Bitmap): PageText {
        val small = withContext(Dispatchers.Default) { page.downscaledCopy(OCR_MAX_SIDE) }
        try {
            var best: Candidate? = null
            for (degrees in ROTATIONS) {
                coroutineContext.ensureActive()
                val image = withContext(Dispatchers.Default) { small.rotatedCopy(degrees) }
                try {
                    val text = PageText(degrees, image.height.toFloat() / image.width, textRecognizer.recognize(image))
                    val candidate = Candidate(text, DocumentClassifier.classify(text.text).bestScore, horizontalShare(text))
                    if (best == null || candidateOrder.compare(candidate, best) > 0) best = candidate
                    if (candidate.isConfident) break
                } finally {
                    if (image !== small) image.recycle()
                }
            }
            return best!!.text
        } finally {
            if (small !== page) small.recycle()
        }
    }

    /** Доля слов из 3+ символов, у которых рамка в пикселях шире, чем выше. */
    private fun horizontalShare(page: PageText): Float {
        val words = page.words.filter { it.text.length >= 3 }
        if (words.isEmpty()) return 0f
        // Рамки в долях страницы: в пикселях ширина = w · W, высота = h · H = h · aspect · W.
        return words.count { it.box.width > it.box.height * page.aspect }.toFloat() / words.size
    }

    private companion object {
        /** Достаточно для текста документа и заметно быстрее полного разрешения камеры. */
        const val OCR_MAX_SIDE = 1800
        const val MIN_HORIZONTAL_SHARE = 0.6f
        val ROTATIONS = listOf(0, 90, 270, 180)
    }
}
