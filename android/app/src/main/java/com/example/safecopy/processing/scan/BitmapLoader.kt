package com.example.safecopy.processing.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Загружает изображение из галереи или из результата системного сканера в память.
 * Учитывает поворот из EXIF и уменьшает картинку, чтобы длинная сторона была не больше [maxSide].
 * На диск ничего не пишется: исходные данные документа не должны оставаться в незашифрованном виде.
 */
class BitmapLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun load(uri: Uri, maxSide: Int = DEFAULT_MAX_SIDE): Bitmap = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Не удалось прочитать изображение")

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSide) sampleSize *= 2
        val decoded = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: throw IOException("Не удалось декодировать изображение")

        val rotation = resolver.openInputStream(uri).use { stream ->
            if (stream == null) 0 else when (
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
        decoded.rotated(rotation).scaledDownTo(maxSide)
    }

    companion object {
        const val DEFAULT_MAX_SIDE = 2400
    }
}

internal fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees % 360 == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { if (it !== this) recycle() }
}

internal fun Bitmap.scaledDownTo(maxSide: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return this
    val factor = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * factor).toInt(), (height * factor).toInt(), true)
        .also { if (it !== this) recycle() }
}
