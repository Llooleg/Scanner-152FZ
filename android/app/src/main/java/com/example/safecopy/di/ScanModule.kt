package com.example.safecopy.di

import com.example.safecopy.processing.scan.DocumentDetector
import com.example.safecopy.processing.scan.OpenCvDocumentDetector
import com.example.safecopy.processing.scan.OpenCvPerspectiveCorrector
import com.example.safecopy.processing.scan.PerspectiveCorrector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {

    @Binds
    abstract fun bindDocumentDetector(impl: OpenCvDocumentDetector): DocumentDetector

    @Binds
    abstract fun bindPerspectiveCorrector(impl: OpenCvPerspectiveCorrector): PerspectiveCorrector
}
