package com.example.safecopy.ui.copyflow

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Общее состояние конвейера «Съёмка → Маскирование → Водяной знак → Экспорт».
 * Живёт, пока в стеке навигации есть граф CopyFlowRoute, и хранит страницы только в памяти:
 * после выхода из конвейера или гибели процесса изображения документа исчезают.
 */
@HiltViewModel
class CopyFlowViewModel @Inject constructor() : ViewModel() {
    private val _pages = MutableStateFlow<List<Bitmap>>(emptyList())
    val pages: StateFlow<List<Bitmap>> = _pages.asStateFlow()

    fun setPages(pages: List<Bitmap>) {
        _pages.value = pages
    }
}
