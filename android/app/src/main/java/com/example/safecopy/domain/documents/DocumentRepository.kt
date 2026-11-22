package com.example.safecopy.domain.documents

import android.graphics.Bitmap
import com.example.safecopy.domain.model.Document
import com.example.safecopy.domain.model.DocumentType
import kotlinx.coroutines.flow.Flow

/**
 * Хранилище документов: метаданные в базе SQLCipher, изображения страниц — в файлах AES-256-GCM.
 * Работает только в разблокированной сессии.
 */
interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>

    /** Сохраняет оригинал документа и возвращает его id. */
    suspend fun save(type: DocumentType, pages: List<Bitmap>): Long

    suspend fun loadPages(documentId: Long): List<Bitmap>

    suspend fun delete(documentId: Long)

    /** Удаляет файлы страниц, не привязанные ни к одному документу (остаются, если процесс убит посреди сохранения). */
    suspend fun removeOrphanedFiles()
}
