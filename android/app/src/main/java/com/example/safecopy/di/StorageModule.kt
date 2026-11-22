package com.example.safecopy.di

import android.content.Context
import com.example.safecopy.data.crypto.VaultKeys
import com.example.safecopy.data.db.VaultDatabase
import com.example.safecopy.data.documents.EncryptedPageStore
import com.example.safecopy.data.documents.RoomDocumentRepository
import com.example.safecopy.domain.auth.SessionManager
import com.example.safecopy.domain.documents.DocumentRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDocumentRepository(
        @ApplicationContext context: Context,
        vault: VaultDatabase,
        sessionManager: SessionManager,
    ): DocumentRepository {
        // noBackupFilesDir: каталог не попадает ни в какие резервные копии.
        val pageStore = EncryptedPageStore(
            directory = File(context.noBackupFilesDir, "vault/pages"),
            key = { VaultKeys.filesKey(sessionManager.requireMasterKey()) },
        )
        return RoomDocumentRepository(vault, pageStore, Dispatchers.IO)
    }
}
