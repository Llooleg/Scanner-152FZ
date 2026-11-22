package com.example.safecopy.ui.documents

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safecopy.domain.documents.DocumentRepository
import com.example.safecopy.domain.model.Document
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentsUiState(
    val documents: List<Document> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    val uiState: StateFlow<DocumentsUiState> = documentRepository.observeDocuments()
        .map { DocumentsUiState(documents = it) }
        .catch { e ->
            // Например, сессию заблокировали, и база закрыта. Экран всё равно сейчас уйдёт на вход.
            Log.w(TAG, "Не удалось прочитать список документов", e)
            emit(DocumentsUiState(loadFailed = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentsUiState(isLoading = true))

    init {
        viewModelScope.launch {
            runCatching { documentRepository.removeOrphanedFiles() }
                .onFailure { Log.w(TAG, "Не удалось убрать лишние файлы страниц", it) }
        }
    }

    private companion object {
        const val TAG = "DocumentsViewModel"
    }
}
