package com.example.safecopy.domain.model

import androidx.annotation.StringRes
import com.example.safecopy.R
import java.time.LocalDate

enum class DocumentType(@param:StringRes val titleRes: Int) {
    PASSPORT_RF(R.string.doc_type_passport),
    SNILS(R.string.doc_type_snils),
    DRIVER_LICENSE(R.string.doc_type_driver_license),
    INN(R.string.doc_type_inn),
    OTHER(R.string.doc_type_other),
}

data class Document(
    val id: Long,
    val type: DocumentType,
    val pageCount: Int,
    val updatedAt: LocalDate,
    val copiesCount: Int,
)
