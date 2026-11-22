package com.example.safecopy.data.documents

import com.example.safecopy.data.crypto.AesGcm
import com.example.safecopy.data.crypto.SealedBox
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Изображения страниц на диске, каждое — отдельный файл AES-256-GCM.
 * Имя файла случайное и входит в AAD: файл нельзя незаметно подменить другим файлом из хранилища.
 *
 * [key] вызывается на каждую операцию: ключ берётся из текущей сессии и не кешируется здесь.
 */
class EncryptedPageStore(
    private val directory: File,
    private val key: () -> SecretKey,
) {

    /** Шифрует и записывает страницу. Возвращает имя файла для базы. */
    fun write(plaintext: ByteArray): String {
        ensureDirectory()
        val name = UUID.randomUUID().toString() + EXTENSION
        val sealed = AesGcm.encrypt(key(), plaintext, aad = name.toByteArray()).toBytes()
        // Сначала во временный файл, потом переименование: оборванная запись не оставит битую страницу.
        val temp = File(directory, "$name.tmp")
        temp.writeBytes(sealed)
        if (!temp.renameTo(File(directory, name))) {
            temp.delete()
            throw IOException("Не удалось сохранить страницу")
        }
        return name
    }

    /** @throws javax.crypto.AEADBadTagException если файл повреждён, подменён или ключ не тот. */
    fun read(name: String): ByteArray {
        val box = SealedBox.fromBytes(fileFor(name).readBytes())
        return AesGcm.decrypt(key(), box, aad = name.toByteArray())
    }

    fun delete(name: String) {
        fileFor(name).delete()
    }

    /** Удаляет файлы, о которых не знает база (например, после сбоя между записью файла и транзакцией). */
    fun deleteAllExcept(knownNames: Set<String>) {
        directory.listFiles()?.forEach { file ->
            if (file.name !in knownNames) file.delete()
        }
    }

    private fun fileFor(name: String): File {
        require(NAME_PATTERN.matches(name)) { "Недопустимое имя файла страницы" }
        return File(directory, name)
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Не удалось создать каталог хранилища")
    }

    private companion object {
        const val EXTENSION = ".page"
        val NAME_PATTERN = Regex("[0-9a-f-]{36}\\.page")
    }
}
