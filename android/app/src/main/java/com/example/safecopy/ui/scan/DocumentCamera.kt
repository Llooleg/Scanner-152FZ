package com.example.safecopy.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.safecopy.processing.scan.GrayFrame
import com.example.safecopy.processing.scan.rotated
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Обёртка над CameraX: превью, анализ кадров для поиска границ и снимок в память.
 * Снимок не сохраняется в файл, поэтому EXIF и временные копии документа на диске не появляются.
 */
class DocumentCamera(
    private val context: Context,
    private val onFrame: (frame: GrayFrame, rotationDegrees: Int) -> Unit,
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    val hasFlash: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        targetRotation: Int,
    ) {
        val provider = ProcessCameraProvider.awaitInstance(context).also { cameraProvider = it }

        // Превью и анализ в одном соотношении 4:3, чтобы рамка из анализа совпадала с картинкой на экране.
        val aspect = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
            .setTargetRotation(targetRotation)
            .build()
            .also { it.surfaceProvider = surfaceProvider }

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector(aspect, Size(640, 480)))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetRotation(targetRotation)
            .build()
            .also { it.setAnalyzer(analysisExecutor, ::analyze) }

        // ~5 Мп хватает для распознавания текста и не переполняет память.
        val capture = ImageCapture.Builder()
            .setResolutionSelector(selector(aspect, Size(2560, 1920)))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetRotation(targetRotation)
            .build()
            .also { imageCapture = it }

        provider.unbindAll()
        camera = provider.bindToLifecycle(
            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, capture,
        )
    }

    /** Делает снимок и возвращает его повёрнутым так, как пользователь видит кадр на экране. */
    suspend fun capture(): Bitmap {
        val capture = checkNotNull(imageCapture) { "Камера не запущена" }
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val result = runCatching {
                            image.use { it.toBitmap().rotated(it.imageInfo.rotationDegrees) }
                        }
                        result.fold(continuation::resume, continuation::resumeWithException)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                },
            )
        }
    }

    fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun release() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
    }

    private fun analyze(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            onFrame(GrayFrame(plane.buffer, it.width, it.height, plane.rowStride), it.imageInfo.rotationDegrees)
        }
    }

    private fun selector(aspect: AspectRatioStrategy, size: Size) = ResolutionSelector.Builder()
        .setAspectRatioStrategy(aspect)
        .setResolutionStrategy(
            ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
        )
        .build()
}

