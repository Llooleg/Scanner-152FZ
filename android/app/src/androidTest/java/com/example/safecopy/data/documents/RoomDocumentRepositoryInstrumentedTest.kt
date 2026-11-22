package com.example.safecopy.data.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.safecopy.data.crypto.VaultKeys
import com.example.safecopy.data.db.SafeCopyDatabase
import com.example.safecopy.data.db.VaultDatabase
import com.example.safecopy.domain.auth.MasterKey
import com.example.safecopy.domain.auth.SessionManager
import com.example.safecopy.domain.model.DocumentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Настоящие SQLCipher и файловая система. Стирает базу приложения на тестовом устройстве. */
@RunWith(AndroidJUnit4::class)
class RoomDocumentRepositoryInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val masterBytes = ByteArray(32) { (it * 7).toByte() }
    private val session = SessionManager()
    private val vault = VaultDatabase(context, session)
    private val pagesDir = File(context.cacheDir, "test_pages")
    private val repository = RoomDocumentRepository(
        vault,
        EncryptedPageStore(pagesDir) { VaultKeys.filesKey(session.requireMasterKey()) },
        Dispatchers.IO,
    )

    @Before
    @After
    fun cleanUp() {
        vault.close()
        context.deleteDatabase(SafeCopyDatabase.NAME)
        pagesDir.deleteRecursively()
    }

    private fun unlock() = session.unlock(MasterKey.adopt(masterBytes.copyOf()))

    private fun page(width: Int, height: Int) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 180, 160)) }

    @Test
    fun saveListLoadAndSurviveRelock() = runBlocking {
        unlock()
        val id = repository.save(DocumentType.PASSPORT_RF, listOf(page(800, 560), page(800, 560)))

        val documents = repository.observeDocuments().first()
        assertEquals(1, documents.size)
        assertEquals(id, documents[0].id)
        assertEquals(DocumentType.PASSPORT_RF, documents[0].type)
        assertEquals(2, documents[0].pageCount)

        // Блокировка закрывает базу; после повторного входа тем же ключом данные на месте.
        session.lock()
        unlock()
        val pages = repository.loadPages(id)
        assertEquals(2, pages.size)
        assertEquals(800, pages[0].width)
        assertEquals(560, pages[0].height)
    }

    @Test
    fun databaseAndPagesAreEncryptedOnDisk() = runBlocking {
        unlock()
        repository.save(DocumentType.SNILS, listOf(page(400, 300)))
        session.lock()

        val header = context.getDatabasePath(SafeCopyDatabase.NAME).readBytes().copyOfRange(0, 16)
        assertFalse("Файл базы — открытый SQLite", String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))

        val pageFile = pagesDir.listFiles()!!.single()
        val start = pageFile.readBytes().copyOfRange(0, 16)
        val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        assertFalse((0..13).any { start.copyOfRange(it, it + 3).contentEquals(jpegMagic) })
    }

    @Test
    fun deleteRemovesRowsAndFiles() = runBlocking {
        unlock()
        val id = repository.save(DocumentType.INN, listOf(page(100, 100), page(100, 100)))
        repository.delete(id)
        assertTrue(repository.observeDocuments().first().isEmpty())
        assertTrue(pagesDir.listFiles().isNullOrEmpty())
    }
}
