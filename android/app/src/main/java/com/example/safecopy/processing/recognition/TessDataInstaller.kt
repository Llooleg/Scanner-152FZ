package com.example.safecopy.processing.recognition

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tesseract читает модель только из файла, а rus.traineddata лежит в assets APK.
 * Копируем её в filesDir/tesseract/tessdata один раз и повторно — после обновления приложения.
 *
 * Модель: tessdata_fast/rus.traineddata из github.com/tesseract-ocr/tessdata_fast, лицензия Apache 2.0.
 */
@Singleton
class TessDataInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Возвращает каталог данных Tesseract (родитель tessdata). */
    @Synchronized
    fun install(): String {
        val root = File(context.filesDir, "tesseract")
        val tessdata = File(root, "tessdata")
        val model = File(tessdata, MODEL_FILE)
        val marker = File(tessdata, "$MODEL_FILE.installed")
        val stamp = installStamp()
        if (model.isFile && model.length() > 0 && marker.isFile && marker.readText() == stamp) return root.absolutePath

        if (!tessdata.isDirectory && !tessdata.mkdirs()) throw IOException("Не удалось создать $tessdata")
        val temp = File(tessdata, "$MODEL_FILE.tmp")
        context.assets.open("tessdata/$MODEL_FILE").use { input -> temp.outputStream().use { input.copyTo(it) } }
        model.delete()
        if (!temp.renameTo(model)) {
            temp.delete()
            throw IOException("Не удалось установить модель распознавания")
        }
        marker.writeText(stamp)
        return root.absolutePath
    }

    private fun installStamp(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
    } catch (_: PackageManager.NameNotFoundException) {
        "0"
    }

    companion object {
        const val LANGUAGE = "rus"
        private const val MODEL_FILE = "$LANGUAGE.traineddata"
    }
}
