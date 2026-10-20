package com.example.safecopy.ui.documents

import androidx.lifecycle.ViewModel
import com.example.safecopy.domain.model.Document
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DocumentsUiState(
    val documents: List<Document> = emptyList(),
)

@HiltViewModel
class DocumentsViewModel @Inject constructor() : ViewModel() {
    // TODO(этап 2): подписка на DocumentRepository (Room + SQLCipher).
    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()
}
