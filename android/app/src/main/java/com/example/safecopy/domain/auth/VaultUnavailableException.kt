package com.example.safecopy.domain.auth

/** Хранилище ключей нельзя открыть, и дело не в неверном PIN: ключ Keystore потерян или данные повреждены. */
class VaultUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
