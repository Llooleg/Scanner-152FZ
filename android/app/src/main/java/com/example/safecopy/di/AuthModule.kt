package com.example.safecopy.di

import android.content.Context
import com.example.safecopy.data.auth.AndroidBiometricAvailability
import com.example.safecopy.data.auth.AndroidKeystoreKeys
import com.example.safecopy.data.auth.BlobStore
import com.example.safecopy.data.auth.KeystoreBiometricKeyRepository
import com.example.safecopy.data.auth.KeystoreDeviceWrapper
import com.example.safecopy.data.auth.KeystorePinRepository
import com.example.safecopy.data.auth.SharedPreferencesBlobStore
import com.example.safecopy.data.auth.SharedPreferencesPinAttemptStore
import com.example.safecopy.domain.auth.BiometricAvailability
import com.example.safecopy.domain.auth.BiometricKeyRepository
import com.example.safecopy.domain.auth.PinAttemptLimiter
import com.example.safecopy.domain.auth.PinRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    private const val KEYS_PREFS = "safecopy_keys"
    private const val ATTEMPTS_PREFS = "safecopy_pin_attempts"

    @Provides
    @Singleton
    fun provideKeystoreKeys(): AndroidKeystoreKeys = AndroidKeystoreKeys()

    @Provides
    @Singleton
    fun provideBlobStore(@ApplicationContext context: Context): BlobStore =
        SharedPreferencesBlobStore(context.getSharedPreferences(KEYS_PREFS, Context.MODE_PRIVATE))

    @Provides
    @Singleton
    fun providePinRepository(store: BlobStore, keys: AndroidKeystoreKeys): PinRepository =
        KeystorePinRepository(store, KeystoreDeviceWrapper(keys))

    @Provides
    @Singleton
    fun provideBiometricKeyRepository(store: BlobStore, keys: AndroidKeystoreKeys): BiometricKeyRepository =
        KeystoreBiometricKeyRepository(store, keys)

    @Provides
    fun provideBiometricAvailability(@ApplicationContext context: Context): BiometricAvailability =
        AndroidBiometricAvailability(context)

    @Provides
    @Singleton
    fun providePinAttemptLimiter(@ApplicationContext context: Context): PinAttemptLimiter =
        PinAttemptLimiter(
            store = SharedPreferencesPinAttemptStore(
                context.getSharedPreferences(ATTEMPTS_PREFS, Context.MODE_PRIVATE)
            ),
            clock = System::currentTimeMillis,
        )
}
