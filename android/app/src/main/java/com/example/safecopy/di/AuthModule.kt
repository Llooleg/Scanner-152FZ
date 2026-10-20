package com.example.safecopy.di

import android.os.SystemClock
import com.example.safecopy.data.auth.InMemoryPinRepository
import com.example.safecopy.domain.auth.PinAttemptLimiter
import com.example.safecopy.domain.auth.PinRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    abstract fun bindPinRepository(impl: InMemoryPinRepository): PinRepository

    companion object {
        @Provides
        @Singleton
        fun providePinAttemptLimiter(): PinAttemptLimiter =
            PinAttemptLimiter(clock = SystemClock::elapsedRealtime)
    }
}
