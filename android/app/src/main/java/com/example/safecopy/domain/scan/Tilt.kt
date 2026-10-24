package com.example.safecopy.domain.scan

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Показание уровня.
 * [degrees] — отклонение экрана от горизонтали.
 * [bubbleX], [bubbleY] — смещение «пузырька» в долях от −1 до 1 в координатах экрана (x вправо, y вниз).
 */
data class TiltReading(val degrees: Float, val bubbleX: Float, val bubbleY: Float)

object Tilt {
    /**
     * Наклон по вектору ускорения свободного падения из TYPE_ACCELEROMETER.
     * Телефон лежит экраном вверх: вся сила тяжести приходится на ось z, наклон 0°.
     */
    fun fromGravity(x: Float, y: Float, z: Float): TiltReading {
        val norm = sqrt(x * x + y * y + z * z)
        if (norm < 1e-3f) return TiltReading(degrees = 90f, bubbleX = 0f, bubbleY = 0f)
        val cos = (abs(z) / norm).coerceAtMost(1f)
        val degrees = Math.toDegrees(acos(cos).toDouble()).toFloat()
        // Пузырёк уходит к приподнятому краю: опущен правый край (x < 0) — пузырёк слева.
        return TiltReading(degrees = degrees, bubbleX = x / norm, bubbleY = -y / norm)
    }
}

/** Фильтр нижних частот: сглаживает дрожание рук в показаниях акселерометра. */
class LowPassFilter(private val alpha: Float = 0.8f) {
    private var state: FloatArray? = null

    fun apply(x: Float, y: Float, z: Float): FloatArray {
        val previous = state ?: return floatArrayOf(x, y, z).also { state = it.copyOf() }
        val next = floatArrayOf(
            alpha * previous[0] + (1 - alpha) * x,
            alpha * previous[1] + (1 - alpha) * y,
            alpha * previous[2] + (1 - alpha) * z,
        )
        state = next
        return next.copyOf()
    }
}
