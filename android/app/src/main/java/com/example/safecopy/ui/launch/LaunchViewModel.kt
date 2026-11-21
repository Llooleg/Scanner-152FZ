package com.example.safecopy.ui.launch

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecopy.domain.auth.PinRepository
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
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            // Загрузка Keystore и проверка записи PIN. Ошибку покажет экран входа, здесь не задерживаемся.
            runCatching { pinRepository.prepare() }
            // TODO(этап 2): открытие базы SQLCipher — после входа, когда есть мастер-ключ.
            // TODO(этап 4): копирование rus.traineddata из assets в filesDir/tesseract/tessdata.
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            // Логотип должен быть виден, даже если инициализация прошла мгновенно.
            delay((MIN_DISPLAY_MILLIS - elapsed).coerceAtLeast(0L))
            _isReady.value = true
        }
    }

    private companion object {
        const val MIN_DISPLAY_MILLIS = 800L
    }
}
