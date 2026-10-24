package com.example.safecopy.processing.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.safecopy.domain.scan.NormPoint
import com.example.safecopy.domain.scan.Quad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/**
 * Проверка поиска границ и выравнивания перспективы на синтетическом «документе»:
 * светлый четырёхугольник с полосками «текста» на тёмном фоне.
 */
@RunWith(AndroidJUnit4::class)
class OpenCvScanTest {

    private val detector = OpenCvDocumentDetector()
    private val corrector = OpenCvPerspectiveCorrector()

    private val width = 800
    private val height = 600
    private val expected = Quad(
        topLeft = NormPoint(150f / width, 100f / height),
        topRight = NormPoint(650f / width, 130f / height),
        bottomRight = NormPoint(620f / width, 520f / height),
        bottomLeft = NormPoint(120f / width, 480f / height),
    )

    @Test
    fun detectsDocumentOnBitmap() {
        val quad = detector.detect(syntheticDocument())
        assertNotNull("Документ не найден", quad)
        assertCornersClose(expected, quad!!)
    }

    @Test
    fun returnsNullWhenNoDocument() {
        val empty = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        assertNull(detector.detect(empty))
    }

    @Test
    fun detectsDocumentInRotatedCameraFrame() {
        // Кадр сенсора лежит «на боку» (поворот 90°): сам документ на экране должен оказаться там же, где на bitmap.
        val upright = syntheticDocument()
        val sensor = upright.rotate(-90)
        val quad = detector.detect(sensor.toGrayFrame(), rotationDegrees = 90)
        assertNotNull("Документ не найден в повёрнутом кадре", quad)
        assertCornersClose(expected, quad!!)
    }

    @Test
    fun correctsPerspectiveToRectangle() {
        val result = corrector.correct(syntheticDocument(), expected)

        // Стороны четырёхугольника ~500x400 пикселей, результат должен быть близок к этому.
        assertTrue("Ширина ${result.width}", result.width in 480..520)
        assertTrue("Высота ${result.height}", result.height in 380..420)
        // Углы результата — светлый документ, а не тёмный фон.
        listOf(
            5 to 5, result.width - 6 to 5, result.width - 6 to result.height - 6, 5 to result.height - 6,
        ).forEach { (x, y) ->
            assertTrue("Угол ($x, $y) тёмный: фон попал в результат", Color.red(result.getPixel(x, y)) > 200)
        }
    }

    private fun syntheticDocument(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(40, 40, 40))
        val path = Path().apply {
            expected.corners.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x * width, p.y * height) else lineTo(p.x * width, p.y * height)
            }
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 230, 220) })
        val text = Paint().apply { color = Color.rgb(90, 90, 90); strokeWidth = 6f }
        for (row in 0 until 6) {
            val y = 200f + row * 45f
            canvas.drawLine(220f, y, 540f, y + 8f, text)
        }
        return bitmap
    }

    private fun assertCornersClose(expected: Quad, actual: Quad, tolerance: Float = 0.03f) {
        expected.corners.zip(actual.corners).forEachIndexed { i, (e, a) ->
            assertEquals("Угол $i по x", e.x, a.x, tolerance)
            assertEquals("Угол $i по y", e.y, a.y, tolerance)
        }
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    /** Имитирует Y-плоскость камеры: яркость пикселей с шагом строки больше ширины. */
    private fun Bitmap.toGrayFrame(): GrayFrame {
        val rowStride = width + 64
        val buffer = ByteBuffer.allocateDirect(rowStride * height)
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = pixels[y * width + x]
                val luma = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
                buffer.put(y * rowStride + x, luma.toByte())
            }
        }
        return GrayFrame(buffer, width, height, rowStride)
    }
}
