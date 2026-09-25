package com.example.powerai.core.data.di

import com.example.powerai.core.data.repository.KnowledgeRepositoryImpl
import com.example.powerai.core.data.repository.EmbeddingRepositoryImpl
import com.example.powerai.core.data.repository.RemoteConfigRepositoryImpl
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.repository.EmbeddingRepository
import com.example.powerai.core.repository.RemoteConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindKnowledgeRepository(
        knowledgeRepositoryImpl: KnowledgeRepositoryImpl
    ): KnowledgeRepository

    @Binds
    @Singleton
    abstract fun bindEmbeddingRepository(
        embeddingRepositoryImpl: EmbeddingRepositoryImpl
    ): EmbeddingRepository

    @Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(
        remoteConfigRepositoryImpl: RemoteConfigRepositoryImpl
    ): RemoteConfigRepository
}
