package com.example.safecopy.domain.recognition

import kotlin.math.max
import kotlin.math.min

/**
 * Прямоугольник в долях страницы: 0..1 по обеим осям, (0, 0) — левый верхний угол.
 * Шаблоны полей, результаты OCR и маски хранятся в этих координатах и не зависят от разрешения снимка.
 */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    init {
        require(left <= right && top <= bottom) { "Некорректный прямоугольник: $this" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    fun intersects(other: NormRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun intersection(other: NormRect): NormRect? =
        if (!intersects(other)) null
        else NormRect(max(left, other.left), max(top, other.top), min(right, other.right), min(bottom, other.bottom))

    /** Наименьший прямоугольник, содержащий оба. */
    fun union(other: NormRect): NormRect =
        NormRect(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

    /** Доля площади этого прямоугольника, попадающая в [other]. */
    fun fractionInside(other: NormRect): Float {
        if (area <= 0f) return if (other.contains(centerX, centerY)) 1f else 0f
        return (intersection(other)?.area ?: 0f) / area
    }

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    /** Расширение на [dx]/[dy] с каждой стороны, с обрезкой по границам страницы. */
    fun expanded(dx: Float, dy: Float): NormRect = NormRect(
        (left - dx).coerceIn(0f, 1f),
        (top - dy).coerceIn(0f, 1f),
        (right + dx).coerceIn(0f, 1f),
        (bottom + dy).coerceIn(0f, 1f),
    )

    /**
     * Этот прямоугольник без [hole]: до четырёх полос вокруг пересечения.
     * Используется, чтобы оставить видимыми последние цифры номера, закрасив всё остальное.
     */
    fun subtract(hole: NormRect): List<NormRect> {
        val cut = intersection(hole) ?: return listOf(this)
        return listOfNotNull(
            NormRect(left, top, right, cut.top).takeIf { it.height > 0f },
            NormRect(left, cut.bottom, right, bottom).takeIf { it.height > 0f },
            NormRect(left, cut.top, cut.left, cut.bottom).takeIf { it.width > 0f },
            NormRect(cut.right, cut.top, right, cut.bottom).takeIf { it.width > 0f },
        )
    }

    companion object {
        val FULL = NormRect(0f, 0f, 1f, 1f)

        /** Из пиксельных координат изображения [imageWidth]×[imageHeight]. */
        fun fromPixels(left: Int, top: Int, right: Int, bottom: Int, imageWidth: Int, imageHeight: Int): NormRect {
            val w = imageWidth.toFloat()
            val h = imageHeight.toFloat()
            return NormRect(
                (left / w).coerceIn(0f, 1f),
                (top / h).coerceIn(0f, 1f),
                (right / w).coerceIn(0f, 1f),
                (bottom / h).coerceIn(0f, 1f),
            )
        }

        fun unionOf(rects: Collection<NormRect>): NormRect? = rects.reduceOrNull(NormRect::union)
    }
}
