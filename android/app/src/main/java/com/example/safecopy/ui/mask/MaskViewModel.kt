package com.example.safecopy.ui.mask

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecopy.domain.model.DocumentType
import com.example.safecopy.domain.recognition.DocumentTemplates
import com.example.safecopy.domain.recognition.FieldKind
import com.example.safecopy.domain.recognition.FieldLocator
import com.example.safecopy.domain.recognition.LocatedPage
import com.example.safecopy.domain.recognition.MaskComposer
import com.example.safecopy.domain.recognition.MaskSettings
import com.example.safecopy.domain.recognition.NormRect
import com.example.safecopy.domain.recognition.NumberLayout
import com.example.safecopy.domain.recognition.NumberMode
import com.example.safecopy.domain.recognition.PageMask
import com.example.safecopy.domain.recognition.PageText
import com.example.safecopy.domain.recognition.Recognition
import com.example.safecopy.domain.recognition.RecognitionCache
import com.example.safecopy.processing.recognition.DocumentRecognizer
import com.example.safecopy.processing.recognition.MaskRenderer
import com.example.safecopy.processing.recognition.NumberStripReader
import com.example.safecopy.processing.recognition.rotatedCopy
import com.example.safecopy.ui.copyflow.CopySource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MaskUiState(
    val isRecognizing: Boolean = true,
    /** OCR не сработал: поля взяты только из шаблона типа документа. */
    val recognitionFailed: Boolean = false,
    val documentType: DocumentType? = null,
    val detectedType: DocumentType? = null,
    /** Страницы, повёрнутые так, чтобы текст был горизонтальным. */
    val pages: List<Bitmap> = emptyList(),
    val located: List<LocatedPage> = emptyList(),
    /** Поля с переключателями в порядке макета. */
    val availableKinds: List<FieldKind> = emptyList(),
    val settings: MaskSettings = MaskSettings(emptySet(), NumberMode.KEEP_LAST_4),
    /** Области, закрашенные вручную, по страницам. */
    val manual: List<List<NormRect>> = emptyList(),
    val currentPage: Int = 0,
    val isDrawing: Boolean = false,
    val isRendering: Boolean = false,
    /** Готовые страницы с впечатанными масками — экран передаёт их дальше по конвейеру. */
    val rendered: List<Bitmap>? = null,
) {
    val masks: List<PageMask> =
        located.mapIndexed { i, page -> MaskComposer.compose(page, settings, manual.getOrElse(i) { emptyList() }) }

    val numberFallback: Boolean get() = masks.any { it.numberFallback }

    val canProceed: Boolean get() = !isRecognizing && !isRendering && pages.isNotEmpty()
}

@HiltViewModel
class MaskViewModel @Inject constructor(
    private val recognizer: DocumentRecognizer,
    private val stripReader: NumberStripReader,
    private val recognitionCache: RecognitionCache,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaskUiState())
    val uiState: StateFlow<MaskUiState> = _uiState.asStateFlow()

    private var startJob: Job? = null

    /** Вызывается экраном, когда оригинал загружен. Повторные вызовы игнорируются. */
    fun start(source: CopySource) {
        if (startJob != null) return
        startJob = viewModelScope.launch {
            val recognition = obtainRecognition(source)
            val upright = withContext(Dispatchers.Default) {
                source.pages.mapIndexed { i, page -> page.rotatedCopy(recognition.pages[i].rotationDegrees) }
            }
            val located = readNumberStrips(FieldLocator.locate(source.type, recognition.pages), upright)
            val kinds = KIND_ORDER.filter { it in DocumentTemplates.kindsFor(source.type) }
            _uiState.update {
                it.copy(
                    isRecognizing = false,
                    documentType = source.type,
                    detectedType = recognition.detectedType,
                    pages = upright,
                    located = located,
                    availableKinds = kinds,
                    settings = MaskSettings.defaults(kinds.toSet()),
                    manual = List(upright.size) { emptyList() },
                )
            }
        }
    }

    private suspend fun obtainRecognition(source: CopySource): Recognition {
        recognitionCache.take(source.documentId)
            ?.takeIf { it.pages.size == source.pages.size }
            ?.let { return it }
        return try {
            recognizer.recognize(source.pages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Распознавание не удалось, остаются только шаблоны", e)
            _uiState.update { it.copy(recognitionFailed = true) }
            Recognition(null, source.pages.map { PageText.empty(it.height.toFloat() / it.width) })
        }
    }

    /** Ищет последние 4 цифры на вертикальных полосах номера. Не нашли — номер там закрашивается целиком. */
    private suspend fun readNumberStrips(pages: List<LocatedPage>, upright: List<Bitmap>): List<LocatedPage> =
        pages.mapIndexed { index, page ->
            val strips = page.fields[FieldKind.NUMBER]?.numbers.orEmpty()
                .filter { it.layout == NumberLayout.VERTICAL_STRIP && it.lastDigits == null }
            val found = strips.associate { strip ->
                strip.area to try {
                    stripReader.findLastDigits(upright[index], strip.area)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Не удалось прочитать номер на полосе", e)
                    null
                }
            }
            page.mapNumbers { number ->
                if (number.layout == NumberLayout.VERTICAL_STRIP && number.lastDigits == null) {
                    number.copy(lastDigits = found[number.area])
                } else {
                    number
                }
            }
        }

    fun onFieldToggle(kind: FieldKind, hidden: Boolean) = _uiState.update { state ->
        val set = if (hidden) state.settings.hidden + kind else state.settings.hidden - kind
        state.copy(settings = state.settings.copy(hidden = set))
    }

    fun onNumberModeChange(mode: NumberMode) = _uiState.update { it.copy(settings = it.settings.copy(numberMode = mode)) }

    fun onPageSelected(index: Int) = _uiState.update {
        if (index in it.pages.indices) it.copy(currentPage = index) else it
    }

    fun onDrawingToggle() = _uiState.update { it.copy(isDrawing = !it.isDrawing) }

    /** Прямоугольник, нарисованный пальцем на текущей странице. Случайные касания отбрасываются. */
    fun onManualRect(rect: NormRect) {
        if (rect.width < MIN_MANUAL_SIDE || rect.height < MIN_MANUAL_SIDE) return
        _uiState.update { state ->
            val page = state.currentPage
            state.copy(manual = state.manual.mapIndexed { i, rects -> if (i == page) rects + rect else rects })
        }
    }

    fun onUndoManual() = _uiState.update { state ->
        val page = state.currentPage
        state.copy(manual = state.manual.mapIndexed { i, rects -> if (i == page) rects.dropLast(1) else rects })
    }

    fun onNext() {
        val state = _uiState.value
        if (!state.canProceed) return
        _uiState.update { it.copy(isRendering = true, isDrawing = false) }
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) {
                state.pages.mapIndexed { i, page -> MaskRenderer.render(page, state.masks[i].rects) }
            }
            _uiState.update { it.copy(isRendering = false, rendered = rendered) }
        }
    }

    fun onRenderedHandled() = _uiState.update { it.copy(rendered = null) }

    private companion object {
        const val TAG = "MaskViewModel"
        const val MIN_MANUAL_SIDE = 0.01f
        val KIND_ORDER = listOf(
            FieldKind.RESIDENCE,
            FieldKind.NUMBER,
            FieldKind.DIVISION_CODE,
            FieldKind.BIRTHPLACE,
            FieldKind.PHOTO,
        )
    }
}
