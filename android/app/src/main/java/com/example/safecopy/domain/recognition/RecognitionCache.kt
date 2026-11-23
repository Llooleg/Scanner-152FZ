package com.example.safecopy.domain.recognition

import com.example.safecopy.domain.auth.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат распознавания, сделанного при сохранении документа, чтобы маскирование не запускало OCR второй раз.
 * Только в памяти; очищается при блокировке: распознанный текст — те же персональные данные.
 */
@Singleton
class RecognitionCache @Inject constructor(sessionManager: SessionManager) {
    private val entries = mutableMapOf<Long, Recognition>()

    init {
        sessionManager.addLockListener(::clear)
    }

    @Synchronized
    fun put(documentId: Long, recognition: Recognition) {
        entries[documentId] = recognition
    }

    /** Забирает результат: повторно он не понадобится. */
    @Synchronized
    fun take(documentId: Long): Recognition? = entries.remove(documentId)

    @Synchronized
    fun clear() = entries.clear()
}
