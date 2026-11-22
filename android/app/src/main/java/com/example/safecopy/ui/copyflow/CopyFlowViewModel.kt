package com.example.safecopy.ui.copyflow

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.safecopy.domain.documents.DocumentRepository
import com.example.safecopy.ui.navigation.CopyFlowRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Общее состояние конвейера «Маскирование → Водяной знак → Экспорт» для сохранённого документа.
 * Живёт, пока в стеке навигации есть граф CopyFlowRoute. Оригинал расшифровывается из хранилища
 * только в память; копия на диск попадает лишь на этапе экспорта, во временный каталог.
 */
@HiltViewModel
class CopyFlowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    val documentId: Long = savedStateHandle.toRoute<CopyFlowRoute>().documentId

    private val _pages = MutableStateFlow<List<Bitmap>>(emptyList())
    val pages: StateFlow<List<Bitmap>> = _pages.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _pages.value = documentRepository.loadPages(documentId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось загрузить страницы документа", e)
            }
        }
    }

    private companion object {
        const val TAG = "CopyFlowViewModel"
    }
}
