package com.example.powerai.di

import com.example.powerai.core.data.repository.KnowledgeRepositoryImpl

import com.example.powerai.core.repository.EmbeddingRepository

import com.example.powerai.core.repository.KnowledgeRepository

import com.example.powerai.data.vision.VisionBoostRepositoryImpl
import com.example.powerai.domain.vision.VisionBoostRepository
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
    abstract fun bindVisionBoostRepository(impl: VisionBoostRepositoryImpl): VisionBoostRepository

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(impl: com.example.powerai.data.repository.SearchHistoryRepositoryImpl): com.example.powerai.domain.repository.SearchHistoryRepository

    @Binds
    @Singleton
    abstract fun bindWebSearchRepository(impl: com.example.powerai.data.repository.WebSearchRepositoryImpl): com.example.powerai.domain.repository.WebSearchRepository

    @Binds
    @Singleton
    abstract fun bindChatHistoryRepository(impl: com.example.powerai.data.repository.ChatHistoryRepositoryImpl): com.example.powerai.domain.repository.ChatHistoryRepository

    @Binds
    @Singleton
    abstract fun bindNativeResourceProvider(
        impl: com.example.powerai.data.util.AndroidNativeResourceProvider
    ): com.example.powerai.core.model.NativeResourceProvider
}
