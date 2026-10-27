package com.example.powerai.core.data.di

import com.example.powerai.core.data.repository.EmbeddingRepositoryImpl
import com.example.powerai.core.data.repository.KnowledgeRepositoryImpl
import com.example.powerai.core.data.repository.RoomFtsRetriever
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.repository.EmbeddingRepository
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.data.repository.ChatHistoryRepositoryImpl
import com.example.powerai.data.repository.SearchHistoryRepositoryImpl
import com.example.powerai.data.repository.WebSearchRepositoryImpl
import com.example.powerai.data.util.AndroidNativeResourceProvider
import com.example.powerai.data.vision.VisionBoostRepositoryImpl
import com.example.powerai.domain.repository.ChatHistoryRepository
import com.example.powerai.domain.repository.SearchHistoryRepository
import com.example.powerai.domain.repository.WebSearchRepository
import com.example.powerai.domain.retrieval.FtsRetriever
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
    abstract fun bindKnowledgeRepository(knowledgeRepositoryImpl: KnowledgeRepositoryImpl): KnowledgeRepository

    @Binds
    @Singleton
    abstract fun bindEmbeddingRepository(embeddingRepositoryImpl: EmbeddingRepositoryImpl): EmbeddingRepository

    @Binds
    @Singleton
    abstract fun bindFtsRetriever(roomFtsRetriever: RoomFtsRetriever): FtsRetriever

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(impl: SearchHistoryRepositoryImpl): SearchHistoryRepository

    @Binds
    @Singleton
    abstract fun bindWebSearchRepository(impl: WebSearchRepositoryImpl): WebSearchRepository

    @Binds
    @Singleton
    abstract fun bindChatHistoryRepository(impl: ChatHistoryRepositoryImpl): ChatHistoryRepository

    @Binds
    @Singleton
    abstract fun bindNativeResourceProvider(impl: AndroidNativeResourceProvider): NativeResourceProvider

    @Binds
    @Singleton
    abstract fun bindVisionBoostRepository(impl: VisionBoostRepositoryImpl): VisionBoostRepository
}
