package com.example.powerai.engine.nativecore.di

import com.example.powerai.data.retriever.DefaultFaissClient
import com.example.powerai.data.retriever.FaissClient
import com.example.powerai.domain.retrieval.QueryEncoder
import com.example.powerai.domain.retrieval.ZeroQueryEncoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * FAISS prototype DI (Phase C27). Production ANN remains [NativeModule.bindAnnRetriever].
 */
@Module
@InstallIn(SingletonComponent::class)
object FaissModule {

    @Provides
    @Singleton
    fun provideQueryEncoder(): QueryEncoder = ZeroQueryEncoder

    @Provides
    @Singleton
    fun provideFaissClient(): FaissClient = DefaultFaissClient
}
