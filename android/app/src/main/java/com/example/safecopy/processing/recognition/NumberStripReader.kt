package com.example.safecopy.processing.recognition

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.safecopy.domain.recognition.NormRect
import com.example.safecopy.domain.recognition.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Последние 4 цифры серии и номера паспорта на вертикальной полосе вдоль края разворота.
 *
 * Полоса вырезается, поворачивается на 90° в обе стороны и распознаётся обычным OCR (без фильтра цифр:
 * с ним перевёрнутый текст читается как правдоподобные цифры). Результат принимается, только если
 * распознаны одни цифры, сгруппированные как «СС СС НННННН» или «СССС НННННН»: при чтении вверх ногами
 * группы идут в обратном порядке (6-2-2), и последними оказались бы цифры серии.
 * Если проверка не прошла, возвращается null, и номер закрашивается целиком.
 */
class NumberStripReader @Inject constructor(
    private val textRecognizer: TextRecognizer,
) {
    suspend fun findLastDigits(page: Bitmap, strip: NormRect): NormRect? {
        val left = floor(strip.left * page.width).toInt().coerceIn(0, page.width - 1)
        val top = floor(strip.top * page.height).toInt().coerceIn(0, page.height - 1)
        val right = ceil(strip.right * page.width).toInt().coerceIn(left + 1, page.width)
        val bottom = ceil(strip.bottom * page.height).toInt().coerceIn(top + 1, page.height)
        val crop = withContext(Dispatchers.Default) { Bitmap.createBitmap(page, left, top, right - left, bottom - top) }
        try {
            var best: Candidate? = null
            for (degrees in listOf(90, 270)) {
                val rotated = withContext(Dispatchers.Default) { crop.rotatedWithMapping(degrees) }
                try {
                    val candidate = read(rotated) ?: continue
                    if (best == null || candidate.confidence > best.confidence) best = candidate
                } finally {
                    if (rotated.bitmap !== crop) rotated.bitmap.recycle()
                }
            }
            val box = best?.boxInCrop ?: return null
            return NormRect(
                ((left + box.left) / page.width).coerceIn(0f, 1f),
                ((top + box.top) / page.height).coerceIn(0f, 1f),
                ((left + box.right) / page.width).coerceIn(0f, 1f),
                ((top + box.bottom) / page.height).coerceIn(0f, 1f),
            )
        } finally {
            if (crop !== page) crop.recycle()
        }
    }

    private class Candidate(val boxInCrop: RectF, val confidence: Float)

    private suspend fun read(image: RotatedImage): Candidate? {
        val words = textRecognizer.recognize(image.bitmap)
            .sortedWith(compareBy({ it.line }, { it.box.left }))
        if (words.isEmpty() || !words.all { word -> word.text.all(Char::isDigit) }) return null
        val groups = words.map { it.text.length }
        if (groups != listOf(2, 2, 6) && groups != listOf(4, 6)) return null
        val digits = words.flatMap { it.symbols }.filter { it.text.length == 1 && it.text[0].isDigit() }
        if (digits.size != NUMBER_DIGITS) return null
        val lastFour = NormRect.unionOf(digits.takeLast(4).map { it.box }) ?: return null
        val w = image.bitmap.width.toFloat()
        val h = image.bitmap.height.toFloat()
        val box = RectF(lastFour.left * w, lastFour.top * h, lastFour.right * w, lastFour.bottom * h)
        image.toSource.mapRect(box)
        return Candidate(box, words.map { it.confidence }.average().toFloat())
    }

    private companion object {
        const val NUMBER_DIGITS = 10
    }
}
