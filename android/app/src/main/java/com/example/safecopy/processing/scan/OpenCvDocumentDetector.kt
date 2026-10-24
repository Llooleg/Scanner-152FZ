package com.example.safecopy.processing.scan

import android.graphics.Bitmap
import com.example.safecopy.domain.scan.FrameGeometry
import com.example.safecopy.domain.scan.Quad
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import kotlin.math.max

/**
 * Классический поиск документа: уменьшение → размытие → границы Кэнни → контуры →
 * самый крупный выпуклый четырёхугольник, занимающий не меньше [MIN_AREA_FRACTION] кадра.
 */
class OpenCvDocumentDetector @Inject constructor() : DocumentDetector {

    override fun detect(frame: GrayFrame, rotationDegrees: Int): Quad? {
        OpenCv.ensureLoaded()
        frame.buffer.rewind()
        val gray = Mat(frame.height, frame.width, CvType.CV_8UC1, frame.buffer, frame.rowStride.toLong())
        return try {
            findQuad(gray, rotationDegrees)
        } finally {
            gray.release()
        }
    }

    override fun detect(bitmap: Bitmap): Quad? {
        OpenCv.ensureLoaded()
        val rgba = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            findQuad(gray, rotationDegrees = 0)
        } finally {
            rgba.release()
            gray.release()
        }
    }

    private fun findQuad(gray: Mat, rotationDegrees: Int): Quad? {
        val width = gray.cols()
        val height = gray.rows()
        val scale = minOf(1.0, WORKING_SIZE / max(width, height))

        val small = Mat()
        val edges = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.resize(gray, small, Size(width * scale, height * scale), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.GaussianBlur(small, small, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(small, edges, CANNY_LOW, CANNY_HIGH)
            // Замыкаем разрывы в контуре: край документа часто прерывается бликами и пальцами.
            Imgproc.dilate(edges, edges, kernel)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val minArea = MIN_AREA_FRACTION * small.cols() * small.rows()
            val best = contours
                .asSequence()
                .filter { Imgproc.contourArea(it) >= minArea }
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(MAX_CANDIDATES)
                .mapNotNull { approximateQuad(it) }
                .firstOrNull()
                ?: return null

            val smallWidth = small.cols()
            val smallHeight = small.rows()
            val corners = best.map { FrameGeometry.toUpright(it.first, it.second, smallWidth, smallHeight, rotationDegrees) }
            return Quad.fromUnordered(corners)
        } finally {
            small.release()
            edges.release()
            kernel.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    /** Аппроксимирует контур многоугольником и возвращает его вершины, если это выпуклый четырёхугольник. */
    private fun approximateQuad(contour: MatOfPoint): List<Pair<Float, Float>>? {
        val curve = MatOfPoint2f(*contour.toArray())
        val approx = MatOfPoint2f()
        val approxInt = MatOfPoint()
        try {
            val perimeter = Imgproc.arcLength(curve, true)
            Imgproc.approxPolyDP(curve, approx, APPROX_EPSILON * perimeter, true)
            val points = approx.toArray()
            if (points.size != 4) return null
            approxInt.fromArray(*points)
            if (!Imgproc.isContourConvex(approxInt)) return null
            return points.map { it.x.toFloat() to it.y.toFloat() }
        } finally {
            curve.release()
            approx.release()
            approxInt.release()
        }
    }

    private companion object {
        /** Сторона уменьшенного кадра: для поиска рамки хватает, а кадры обрабатываются быстро. */
        const val WORKING_SIZE = 400.0
        const val CANNY_LOW = 30.0
        const val CANNY_HIGH = 100.0
        const val MIN_AREA_FRACTION = 0.15
        const val APPROX_EPSILON = 0.02
        const val MAX_CANDIDATES = 5
    }
}
