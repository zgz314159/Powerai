package com.example.powerai.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FaissModule {

    // AnnRetriever is bound once in engine NativeModule (@Binds NativeAnnRetriever).
    // A second @Provides here caused Dagger DuplicateBindings during hiltJavaCompileDebug.

    @Provides
    @Singleton
    fun provideQueryEncoder(): com.example.powerai.domain.retrieval.QueryEncoder =
        com.example.powerai.domain.retrieval.ZeroQueryEncoder

    @Provides
    @Singleton
    fun provideFaissClient(): com.example.powerai.data.retriever.FaissClient =
        com.example.powerai.data.retriever.DefaultFaissClient
}

