package com.example.safecopy.processing.scan

import android.graphics.Bitmap
import com.example.safecopy.domain.scan.Quad
import java.nio.ByteBuffer

/**
 * Полутоновый кадр камеры: Y-плоскость YUV_420_888 без копирования.
 * [rowStride] может быть больше [width] — так устроены буферы камеры на многих устройствах.
 */
class GrayFrame(
    val buffer: ByteBuffer,
    val width: Int,
    val height: Int,
    val rowStride: Int,
)

/** Поиск границ документа на изображении. */
interface DocumentDetector {
    /** Ищет документ в кадре камеры. Углы возвращаются для кадра, повёрнутого на [rotationDegrees]. */
    fun detect(frame: GrayFrame, rotationDegrees: Int): Quad?

    /** Ищет документ на уже повёрнутом изображении (снимок, импорт из галереи). */
    fun detect(bitmap: Bitmap): Quad?
}

/** Выравнивание перспективы: вырезает документ по углам и превращает его в прямоугольник. */
interface PerspectiveCorrector {
    fun correct(bitmap: Bitmap, quad: Quad): Bitmap
}
