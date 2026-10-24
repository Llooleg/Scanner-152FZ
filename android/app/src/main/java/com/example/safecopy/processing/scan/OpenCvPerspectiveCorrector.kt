package com.example.safecopy.processing.scan

import android.graphics.Bitmap
import com.example.safecopy.domain.scan.NormPoint
import com.example.safecopy.domain.scan.Quad
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

class OpenCvPerspectiveCorrector @Inject constructor() : PerspectiveCorrector {

    override fun correct(bitmap: Bitmap, quad: Quad): Bitmap {
        OpenCv.ensureLoaded()
        val w = bitmap.width.toDouble()
        val h = bitmap.height.toDouble()
        fun NormPoint.px() = Point(x * w, y * h)

        val tl = quad.topLeft.px()
        val tr = quad.topRight.px()
        val br = quad.bottomRight.px()
        val bl = quad.bottomLeft.px()

        // Размер результата — по длинам сторон четырёхугольника, но не больше MAX_OUTPUT_SIDE:
        // для распознавания этого достаточно, а лишние мегапиксели только тратят память.
        var outWidth = max(distance(tl, tr), distance(bl, br))
        var outHeight = max(distance(tl, bl), distance(tr, br))
        val downscale = MAX_OUTPUT_SIDE / max(outWidth, outHeight)
        if (downscale < 1.0) {
            outWidth *= downscale
            outHeight *= downscale
        }
        val targetWidth = outWidth.roundToInt().coerceAtLeast(1)
        val targetHeight = outHeight.roundToInt().coerceAtLeast(1)

        val src = Mat()
        val dst = Mat()
        val srcPoints = MatOfPoint2f(tl, tr, br, bl)
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(targetWidth - 1.0, 0.0),
            Point(targetWidth - 1.0, targetHeight - 1.0),
            Point(0.0, targetHeight - 1.0),
        )
        val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.warpPerspective(
                src, dst, transform, Size(targetWidth.toDouble(), targetHeight.toDouble()), Imgproc.INTER_LINEAR,
            )
            return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also {
                Utils.matToBitmap(dst, it)
            }
        } finally {
            src.release()
            dst.release()
            srcPoints.release()
            dstPoints.release()
            transform.release()
        }
    }

    private fun distance(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    private companion object {
        const val MAX_OUTPUT_SIDE = 2400.0
    }
}
