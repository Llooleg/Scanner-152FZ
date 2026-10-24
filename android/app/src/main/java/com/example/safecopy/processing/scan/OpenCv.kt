package com.example.safecopy.processing.scan

import org.opencv.android.OpenCVLoader

/** Однократная загрузка нативной библиотеки OpenCV, поставляемой в APK. */
internal object OpenCv {
    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (!loaded) {
                check(OpenCVLoader.initLocal()) { "Не удалось загрузить нативную библиотеку OpenCV" }
                loaded = true
            }
        }
    }
}
