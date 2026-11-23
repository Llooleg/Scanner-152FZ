package com.example.safecopy.ui.launch

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecopy.domain.auth.PinRepository
import com.example.safecopy.processing.recognition.TesseractTextRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LaunchViewModel @Inject constructor(
    private val pinRepository: PinRepository,
    private val textRecognizer: TesseractTextRecognizer,
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            // Загрузка Keystore и проверка записи PIN. Ошибку покажет экран входа, здесь не задерживаемся.
            runCatching { pinRepository.prepare() }
            // База SQLCipher открывается после входа, когда есть мастер-ключ (VaultDatabase).
            // Модель rus.traineddata копируется из assets и загружается в Tesseract. Если не вышло,
            // экран маскирования повторит попытку и при неудаче закрасит поля по шаблонам.
            runCatching { textRecognizer.warmUp() }
                .onFailure { Log.w(TAG, "Не удалось подготовить распознавание текста", it) }
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            // Логотип должен быть виден, даже если инициализация прошла мгновенно.
            delay((MIN_DISPLAY_MILLIS - elapsed).coerceAtLeast(0L))
            _isReady.value = true
        }
    }

    private companion object {
        const val MIN_DISPLAY_MILLIS = 800L
        const val TAG = "LaunchViewModel"
    }
}
