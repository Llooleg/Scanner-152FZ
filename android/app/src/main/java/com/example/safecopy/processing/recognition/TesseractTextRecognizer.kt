package com.example.safecopy.processing.recognition

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.safecopy.domain.recognition.NormRect
import com.example.safecopy.domain.recognition.OcrSymbol
import com.example.safecopy.domain.recognition.OcrWord
import com.example.safecopy.domain.recognition.TextRecognizer
import com.googlecode.tesseract.android.ResultIterator
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_SYMBOL
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_WORD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tesseract (Tesseract4Android), русская LSTM-модель, без сети.
 * TessBaseAPI не потокобезопасен, поэтому один экземпляр на приложение и доступ через мьютекс.
 * После каждого распознавания изображение выгружается из движка ([TessBaseAPI.clear]).
 */
@Singleton
class TesseractTextRecognizer @Inject constructor(
    private val installer: TessDataInstaller,
) : TextRecognizer {

    private val mutex = Mutex()
    private var api: TessBaseAPI? = null

    /** Установка модели и инициализация движка заранее — на Launch Screen. */
    suspend fun warmUp() {
        mutex.withLock { withContext(Dispatchers.IO) { ensureApi() } }
    }

    override suspend fun recognize(image: Bitmap): List<OcrWord> = mutex.withLock {
        withContext(Dispatchers.Default) {
            val api = ensureApi()
            try {
                api.setImage(image)
                // getUTF8Text запускает распознавание; итератор без него пуст.
                if (api.getUTF8Text().isNullOrBlank()) return@withContext emptyList()
                val iterator = api.getResultIterator() ?: return@withContext emptyList()
                try {
                    readWords(iterator, image.width, image.height)
                } finally {
                    iterator.delete()
                }
            } finally {
                api.clear()
            }
        }
    }

    private fun ensureApi(): TessBaseAPI {
        api?.let { return it }
        val dataPath = installer.install()
        val created = TessBaseAPI()
        if (!created.init(dataPath, TessDataInstaller.LANGUAGE, TessBaseAPI.OEM_LSTM_ONLY)) {
            created.recycle()
            throw IllegalStateException("Не удалось инициализировать Tesseract")
        }
        // Снимки страниц не несут DPI; без подсказки Tesseract предупреждает и хуже оценивает размер символов.
        created.setVariable("user_defined_dpi", "300")
        created.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        api = created
        return created
    }

    private fun readWords(iterator: ResultIterator, width: Int, height: Int): List<OcrWord> {
        val words = mutableListOf<OcrWord>()
        var line = -1
        var wordText: String? = null
        var wordBox: NormRect? = null
        var wordConfidence = 0f
        var symbols = mutableListOf<OcrSymbol>()

        fun flush() {
            val text = wordText?.trim()
            val box = wordBox
            if (!text.isNullOrEmpty() && box != null) {
                words += OcrWord(text, box, wordConfidence, line, symbols)
            }
            wordText = null
            wordBox = null
            symbols = mutableListOf()
        }

        fun Rect.normalized() = NormRect.fromPixels(left, top, right, bottom, width, height)

        iterator.begin()
        do {
            if (iterator.isAtBeginningOf(RIL_TEXTLINE)) line++
            if (iterator.isAtBeginningOf(RIL_WORD)) {
                flush()
                wordText = iterator.getUTF8Text(RIL_WORD)
                wordBox = iterator.getBoundingRect(RIL_WORD)?.normalized()
                wordConfidence = iterator.confidence(RIL_WORD)
            }
            val symbol = iterator.getUTF8Text(RIL_SYMBOL)
            val symbolBox = iterator.getBoundingRect(RIL_SYMBOL)
            if (!symbol.isNullOrEmpty() && symbolBox != null) symbols += OcrSymbol(symbol, symbolBox.normalized())
        } while (iterator.next(RIL_SYMBOL))
        flush()
        return words
    }
}
