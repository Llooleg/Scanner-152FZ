package com.example.safecopy.processing.recognition

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF

/*
 * Преобразования страниц для распознавания. В отличие от помощников съёмки, исходник не освобождается:
 * оригинал страницы ещё нужен маскированию.
 */

/** Повёрнутая по часовой стрелке копия (или сам bitmap, если поворот 0). */
internal fun Bitmap.rotatedCopy(degrees: Int): Bitmap {
    if (degrees % 360 == 0) return this
    return Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}

/** Уменьшенная копия с длинной стороной не больше [maxSide] (или сам bitmap, если он уже меньше). */
internal fun Bitmap.downscaledCopy(maxSide: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return this
    val factor = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * factor).toInt().coerceAtLeast(1), (height * factor).toInt().coerceAtLeast(1), true)
}

/**
 * Поворот на [degrees] с обратным отображением: [toSource] переводит пиксели повёрнутого изображения
 * в пиксели исходного. Bitmap.createBitmap после поворота сдвигает картинку в (0, 0), это учтено.
 */
internal class RotatedImage(val bitmap: Bitmap, val toSource: Matrix)

internal fun Bitmap.rotatedWithMapping(degrees: Int): RotatedImage {
    val rotation = Matrix().apply { postRotate(degrees.toFloat()) }
    val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
    rotation.mapRect(bounds)
    val forward = Matrix(rotation).apply { postTranslate(-bounds.left, -bounds.top) }
    val inverse = Matrix()
    check(forward.invert(inverse)) { "Матрица поворота не обратима" }
    return RotatedImage(rotatedCopy(degrees), inverse)
}
