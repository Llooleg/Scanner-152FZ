package com.example.safecopy.domain.recognition

import android.graphics.Bitmap

/**
 * Распознавание текста на устройстве с автоматической разметкой блоков.
 * Рамки слов и символов — в долях переданного изображения.
 *
 * Режима «только цифры» нет намеренно: с таким фильтром Tesseract читает перевёрнутый текст
 * как правдоподобный набор цифр, и отличить ошибку становится невозможно.
 */
interface TextRecognizer {
    suspend fun recognize(image: Bitmap): List<OcrWord>
}
