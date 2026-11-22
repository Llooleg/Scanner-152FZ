package com.example.safecopy.data.db

import android.content.Context
import androidx.room.Room
import com.example.safecopy.data.crypto.VaultKeys
import com.example.safecopy.domain.auth.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * База SQLCipher, открытая ключом текущей сессии. Открывается при первом обращении после входа
 * и закрывается при блокировке: без мастер-ключа база на диске — шифртекст.
 */
@Singleton
class VaultDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
) {
    private var database: SafeCopyDatabase? = null

    init {
        sessionManager.addLockListener(::close)
    }

    /** @throws IllegalStateException если сессия заблокирована. */
    @Synchronized
    fun get(): SafeCopyDatabase = database ?: open().also { database = it }

    @Synchronized
    fun close() {
        database?.close()
        database = null
    }

    private fun open(): SafeCopyDatabase {
        System.loadLibrary("sqlcipher")
        val key = VaultKeys.databaseKey(sessionManager.requireMasterKey())
        val passphrase = try {
            VaultKeys.sqlCipherRawKey(key)
        } finally {
            key.fill(0)
        }
        return Room.databaseBuilder(context, SafeCopyDatabase::class.java, SafeCopyDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }
}
