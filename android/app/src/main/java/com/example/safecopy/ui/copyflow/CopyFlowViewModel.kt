package com.example.safecopy.ui.copyflow

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.safecopy.domain.documents.DocumentRepository
import com.example.safecopy.domain.model.DocumentType
import com.example.safecopy.ui.navigation.CopyFlowRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Оригинал документа, расшифрованный в память для создания копии. */
class CopySource(val documentId: Long, val type: DocumentType, val pages: List<Bitmap>)

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

    private val _source = MutableStateFlow<CopySource?>(null)
    val source: StateFlow<CopySource?> = _source.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    /** Страницы после маскирования — вход для водяного знака. */
    private val _maskedPages = MutableStateFlow<List<Bitmap>>(emptyList())
    val maskedPages: StateFlow<List<Bitmap>> = _maskedPages.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val type = documentRepository.getType(documentId) ?: error("Документ $documentId не найден")
                val pages = documentRepository.loadPages(documentId)
                check(pages.isNotEmpty()) { "У документа нет страниц" }
                _source.value = CopySource(documentId, type, pages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось загрузить документ", e)
                _loadFailed.value = true
            }
        }
    }

    fun setMaskedPages(pages: List<Bitmap>) {
        _maskedPages.value = pages
    }

    private companion object {
        const val TAG = "CopyFlowViewModel"
    }
}
