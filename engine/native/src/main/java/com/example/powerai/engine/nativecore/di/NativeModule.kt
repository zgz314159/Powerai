package com.example.powerai.engine.nativecore.di

import com.example.powerai.engine.nativecore.NativeVectorRepository
import com.example.powerai.engine.nativecore.NativeAnnRetriever
import com.example.powerai.core.repository.VectorRepository
import com.example.powerai.core.repository.AnnRetriever
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NativeModule {

    @Binds
    @Singleton
    abstract fun bindVectorRepository(
        nativeVectorRepository: NativeVectorRepository
    ): VectorRepository

    @Binds
    @Singleton
    abstract fun bindAnnRetriever(
        nativeAnnRetriever: NativeAnnRetriever
    ): AnnRetriever
}
