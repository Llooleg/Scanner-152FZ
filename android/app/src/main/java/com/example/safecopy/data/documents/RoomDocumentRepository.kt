package com.example.safecopy.data.documents

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.safecopy.data.db.DocumentEntity
import com.example.safecopy.data.db.DocumentSummary
import com.example.safecopy.data.db.PageEntity
import com.example.safecopy.data.db.VaultDatabase
import com.example.safecopy.domain.documents.DocumentRepository
import com.example.safecopy.domain.model.Document
import com.example.safecopy.domain.model.DocumentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

class RoomDocumentRepository(
    private val vault: VaultDatabase,
    private val pageStore: EncryptedPageStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : DocumentRepository {

    // Сохранение пишет файлы раньше транзакции; уборка «сирот» не должна попасть в этот промежуток.
    private val fileMutex = Mutex()

    private val dao get() = vault.get().documentDao()

    override fun observeDocuments(): Flow<List<Document>> =
        flow { emitAll(dao.observeSummaries()) }
            .map { summaries -> summaries.map { it.toDocument() } }
            .flowOn(ioDispatcher)

    override suspend fun save(type: DocumentType, pages: List<Bitmap>): Long = withContext(ioDispatcher) {
        require(pages.isNotEmpty()) { "Документ без страниц" }
        fileMutex.withLock {
            val written = mutableListOf<String>()
            try {
                val entities = pages.mapIndexed { index, bitmap ->
                    val name = pageStore.write(bitmap.toJpeg())
                    written += name
                    PageEntity(pageIndex = index, fileName = name, width = bitmap.width, height = bitmap.height)
                }
                val now = clock()
                dao.insertWithPages(DocumentEntity(type = type.name, createdAt = now, updatedAt = now), entities)
            } catch (e: Exception) {
                written.forEach(pageStore::delete)
                throw e
            }
        }
    }

    override suspend fun loadPages(documentId: Long): List<Bitmap> = withContext(ioDispatcher) {
        dao.pages(documentId).map { page ->
            val bytes = pageStore.read(page.fileName)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("Не удалось декодировать страницу ${page.pageIndex}")
        }
    }

    override suspend fun delete(documentId: Long) = withContext(ioDispatcher) {
        fileMutex.withLock {
            val files = dao.pages(documentId).map { it.fileName }
            dao.delete(documentId)
            files.forEach(pageStore::delete)
        }
    }

    override suspend fun removeOrphanedFiles() = withContext(ioDispatcher) {
        fileMutex.withLock {
            pageStore.deleteAllExcept(dao.allFileNames().toSet())
        }
    }

    private fun DocumentSummary.toDocument() = Document(
        id = id,
        type = DocumentType.entries.firstOrNull { it.name == type } ?: DocumentType.OTHER,
        pageCount = pageCount,
        updatedAt = Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
        // TODO(этап 7): считать по журналу отправок.
        copiesCount = 0,
    )

    /**
     * Оригинал хранится в JPEG высокого качества: PNG страницы паспорта весит в разы больше.
     * Bitmap.compress не пишет EXIF, так что метаданные исходного снимка сюда не попадают.
     */
    private fun Bitmap.toJpeg(): ByteArray =
        ByteArrayOutputStream().use { out ->
            if (!compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("Не удалось сжать страницу")
            out.toByteArray()
        }

    private companion object {
        const val JPEG_QUALITY = 95
    }
}
