package com.example.safecopy.domain.scan

import kotlin.math.abs
import kotlin.math.max

/** Точка в нормированных координатах кадра (0..1 по ширине и высоте), кадр повёрнут «как на экране». */
data class NormPoint(val x: Float, val y: Float)

/** Четырёхугольник документа в кадре. Углы всегда упорядочены по часовой стрелке от левого верхнего. */
data class Quad(
    val topLeft: NormPoint,
    val topRight: NormPoint,
    val bottomRight: NormPoint,
    val bottomLeft: NormPoint,
) {
    val corners: List<NormPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** Наибольшее смещение угла относительно [other]: по нему отличаем дрожание рамки от устойчивой находки. */
    fun maxCornerShift(other: Quad): Float =
        corners.zip(other.corners).maxOf { (a, b) -> max(abs(a.x - b.x), abs(a.y - b.y)) }

    companion object {
        /**
         * Упорядочивает 4 произвольные точки: у левого верхнего минимальная сумма x + y,
         * у правого нижнего максимальная, у правого верхнего минимальная разность y − x, у левого нижнего максимальная.
         */
        fun fromUnordered(points: List<NormPoint>): Quad {
            require(points.size == 4) { "Нужно ровно 4 точки, получено ${points.size}" }
            return Quad(
                topLeft = points.minBy { it.x + it.y },
                topRight = points.minBy { it.y - it.x },
                bottomRight = points.maxBy { it.x + it.y },
                bottomLeft = points.maxBy { it.y - it.x },
            )
        }
    }
}

object FrameGeometry {
    /**
     * Переводит точку из координат кадра сенсора (ширина [width], высота [height]) в нормированные координаты
     * кадра, повёрнутого на [rotationDegrees] по часовой стрелке, то есть так, как его видит пользователь.
     */
    fun toUpright(x: Float, y: Float, width: Int, height: Int, rotationDegrees: Int): NormPoint {
        val nx = x / width
        val ny = y / height
        return when (rotationDegrees) {
            0 -> NormPoint(nx, ny)
            90 -> NormPoint(1f - ny, nx)
            180 -> NormPoint(1f - nx, 1f - ny)
            270 -> NormPoint(ny, 1f - nx)
            else -> throw IllegalArgumentException("Поворот должен быть кратен 90°: $rotationDegrees")
        }
    }
}
