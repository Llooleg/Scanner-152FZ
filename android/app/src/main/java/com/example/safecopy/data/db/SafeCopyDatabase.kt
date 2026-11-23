package com.example.safecopy.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Имя [com.example.safecopy.domain.model.DocumentType]. */
    val type: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("document_id")],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long = 0,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    /** Имя зашифрованного файла в каталоге страниц. */
    @ColumnInfo(name = "file_name") val fileName: String,
    val width: Int,
    val height: Int,
)

data class DocumentSummary(
    val id: Long,
    val type: String,
    val updatedAt: Long,
    val pageCount: Int,
)

@Dao
abstract class DocumentDao {

    @Query(
        """
        SELECT d.id, d.type, d.updated_at AS updatedAt,
               (SELECT COUNT(*) FROM pages p WHERE p.document_id = d.id) AS pageCount
        FROM documents d
        ORDER BY d.updated_at DESC, d.id DESC
        """
    )
    abstract fun observeSummaries(): Flow<List<DocumentSummary>>

    @Query("SELECT type FROM documents WHERE id = :documentId")
    abstract suspend fun type(documentId: Long): String?

    @Query("SELECT * FROM pages WHERE document_id = :documentId ORDER BY page_index")
    abstract suspend fun pages(documentId: Long): List<PageEntity>

    @Query("SELECT file_name FROM pages")
    abstract suspend fun allFileNames(): List<String>

    @Query("DELETE FROM documents WHERE id = :documentId")
    abstract suspend fun delete(documentId: Long)

    @Insert
    protected abstract suspend fun insertDocument(document: DocumentEntity): Long

    @Insert
    protected abstract suspend fun insertPages(pages: List<PageEntity>)

    @Transaction
    open suspend fun insertWithPages(document: DocumentEntity, pages: List<PageEntity>): Long {
        val id = insertDocument(document)
        insertPages(pages.map { it.copy(documentId = id) })
        return id
    }
}

@Database(entities = [DocumentEntity::class, PageEntity::class], version = 1, exportSchema = true)
abstract class SafeCopyDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        const val NAME = "safecopy.db"
    }
}
