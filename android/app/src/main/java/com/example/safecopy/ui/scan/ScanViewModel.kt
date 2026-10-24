package com.example.safecopy.ui.scan

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecopy.domain.scan.AutoCaptureController
import com.example.safecopy.domain.scan.AutoCaptureState
import com.example.safecopy.domain.scan.Quad
import com.example.safecopy.domain.scan.TiltReading
import com.example.safecopy.processing.scan.BitmapLoader
import com.example.safecopy.processing.scan.DocumentDetector
import com.example.safecopy.processing.scan.GrayFrame
import com.example.safecopy.processing.scan.PerspectiveCorrector
import com.example.safecopy.processing.scan.TiltSensor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class ScanMessage { CameraUnavailable, CaptureFailed, ImportFailed, ScannerUnavailable }

data class ScanUiState(
    val quad: Quad? = null,
    val tilt: TiltReading? = null,
    val isTiltSensorAvailable: Boolean = true,
    val autoCaptureEnabled: Boolean = true,
    val autoCaptureState: AutoCaptureState = AutoCaptureState.Searching,
    val torchEnabled: Boolean = false,
    val isProcessing: Boolean = false,
    /** После автоснимка ждём, пока документ уйдёт из кадра, чтобы не снять ту же страницу второй раз. */
    val waitingForNextPage: Boolean = false,
    val pages: List<Bitmap> = emptyList(),
    val message: ScanMessage? = null,
) {
    val canCaptureMore: Boolean get() = pages.size < MAX_PAGES

    companion object {
        /** Разворот паспорта и страница регистрации, с запасом для других документов. */
        const val MAX_PAGES = 4
    }
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val detector: DocumentDetector,
    private val corrector: PerspectiveCorrector,
    private val bitmapLoader: BitmapLoader,
    private val tiltSensor: TiltSensor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState(isTiltSensorAvailable = tiltSensor.isAvailable))
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val captureRequests = Channel<Unit>(Channel.CONFLATED)

    /** Сигналы экрану сделать снимок: по автоспуску или по кнопке. */
    val captureEvents: Flow<Unit> = captureRequests.receiveAsFlow()

    private val autoCapture = AutoCaptureController(clock = SystemClock::elapsedRealtime)

    // Кадры приходят из потока анализа камеры, а автоспуск — состояние с памятью, поэтому доступ под замком.
    private val frameLock = Any()

    init {
        viewModelScope.launch {
            tiltSensor.readings().collect { reading -> _uiState.update { it.copy(tilt = reading) } }
        }
    }

    /** Вызывается из потока анализа CameraX для каждого кадра. */
    fun onFrame(frame: GrayFrame, rotationDegrees: Int) {
        val quad = runCatching { detector.detect(frame, rotationDegrees) }
            .onFailure { Log.w(TAG, "Ошибка поиска границ", it) }
            .getOrNull()
        val state = _uiState.value
        val autoState = synchronized(frameLock) {
            // Без акселерометра автоспуск ориентируется только на рамку документа.
            autoCapture.update(quad, tiltDegrees = state.tilt?.degrees ?: 0f)
        }
        val waiting = state.waitingForNextPage && quad != null
        _uiState.update { it.copy(quad = quad, autoCaptureState = autoState, waitingForNextPage = waiting) }

        if (autoState == AutoCaptureState.Ready && state.autoCaptureEnabled && !waiting &&
            !state.isProcessing && state.canCaptureMore
        ) {
            _uiState.update { it.copy(isProcessing = true) }
            captureRequests.trySend(Unit)
        }
    }

    fun onShutterClick() {
        val state = _uiState.value
        if (state.isProcessing || !state.canCaptureMore) return
        _uiState.update { it.copy(isProcessing = true) }
        captureRequests.trySend(Unit)
    }

    /** Снимок с камеры: ищем документ на полном кадре и выравниваем перспективу. */
    fun onPhotoCaptured(photo: Bitmap) {
        val fallbackQuad = _uiState.value.quad
        processPage { straighten(photo, fallbackQuad) }
    }

    fun onCaptureFailed(error: Throwable) {
        Log.w(TAG, "Снимок не удался", error)
        finishProcessing(message = ScanMessage.CaptureFailed)
    }

    /** Импорт из галереи: если рамка не найдена, берём изображение целиком. */
    fun onImageImported(uri: Uri) {
        _uiState.update { it.copy(isProcessing = true) }
        processPage(onError = ScanMessage.ImportFailed) {
            straighten(bitmapLoader.load(uri), fallbackQuad = null)
        }
    }

    /** Страницы от ML Kit Document Scanner уже выровнены. Их временные файлы сразу удаляем. */
    fun onScannerPages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            try {
                val free = ScanUiState.MAX_PAGES - _uiState.value.pages.size
                val pages = uris.take(free).map { bitmapLoader.load(it) }
                _uiState.update { it.copy(pages = it.pages + pages) }
                finishProcessing()
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось загрузить страницы сканера", e)
                finishProcessing(message = ScanMessage.ImportFailed)
            } finally {
                withContext(Dispatchers.IO) { uris.forEach { uri -> uri.path?.let { File(it).delete() } } }
            }
        }
    }

    fun onCameraUnavailable(error: Throwable) {
        Log.w(TAG, "Не удалось запустить камеру", error)
        _uiState.update { it.copy(message = ScanMessage.CameraUnavailable) }
    }

    fun onScannerUnavailable(error: Throwable) {
        Log.w(TAG, "ML Kit Document Scanner недоступен", error)
        _uiState.update { it.copy(message = ScanMessage.ScannerUnavailable) }
    }

    fun onRemovePage(index: Int) {
        _uiState.update { state -> state.copy(pages = state.pages.filterIndexed { i, _ -> i != index }) }
    }

    fun onAutoCaptureToggle() {
        _uiState.update { it.copy(autoCaptureEnabled = !it.autoCaptureEnabled) }
    }

    fun onTorchToggle() {
        _uiState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun processPage(onError: ScanMessage = ScanMessage.CaptureFailed, block: suspend () -> Bitmap) {
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.Default) { block() }
                _uiState.update { it.copy(pages = it.pages + page, waitingForNextPage = true) }
                finishProcessing()
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось обработать страницу", e)
                finishProcessing(message = onError)
            }
        }
    }

    private fun straighten(photo: Bitmap, fallbackQuad: Quad?): Bitmap {
        val quad = detector.detect(photo) ?: fallbackQuad ?: return photo
        return corrector.correct(photo, quad).also { photo.recycle() }
    }

    private fun finishProcessing(message: ScanMessage? = null) {
        synchronized(frameLock) { autoCapture.reset() }
        _uiState.update { it.copy(isProcessing = false, message = message ?: it.message) }
    }

    private companion object {
        const val TAG = "ScanViewModel"
    }
}
