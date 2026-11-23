package com.example.safecopy.di

import com.example.safecopy.domain.recognition.TextRecognizer
import com.example.safecopy.processing.recognition.TesseractTextRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RecognitionModule {

    @Binds
    abstract fun bindTextRecognizer(impl: TesseractTextRecognizer): TextRecognizer
}
