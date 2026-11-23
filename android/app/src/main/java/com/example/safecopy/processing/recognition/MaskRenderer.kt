package com.example.safecopy.processing.recognition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.safecopy.domain.recognition.NormRect
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Впечатывает маски в пиксели копии страницы сплошной заливкой. Размытие не используется:
 * его можно частично обратить. Границы округляются наружу, чтобы на краях не оставалось полупрозрачных пикселей.
 */
object MaskRenderer {

    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    fun render(page: Bitmap, rects: List<NormRect>): Bitmap {
        val out = page.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        for (rect in rects) {
            canvas.drawRect(
                floor(rect.left * w),
                floor(rect.top * h),
                ceil(rect.right * w),
                ceil(rect.bottom * h),
                paint,
            )
        }
        return out
    }
}
