package com.example.powerai.di

import com.example.powerai.core.data.repository.KnowledgeRepositoryImpl

import com.example.powerai.core.repository.EmbeddingRepository

import com.example.powerai.core.repository.KnowledgeRepository

import com.example.powerai.data.importer.AppDatabaseKbPort
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.vision.VisionBoostRepositoryImpl
import com.example.powerai.domain.usecase.DatabaseUseCase
import com.example.powerai.domain.vision.VisionBoostRepository
import com.example.powerai.feature.database.DatabaseKbPort
import com.example.powerai.feature.hybrid.HybridKbImportPort
import com.example.powerai.feature.knowledgedetail.KnowledgeDetailSupportPort
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
    abstract fun bindChatHistoryRepository(impl: com.example.powerai.data.repository.ChatHistoryRepositoryImpl): com.example.powerai.domain.repository.ChatHistoryRepository

    @Binds
    @Singleton
    abstract fun bindNativeResourceProvider(
        impl: com.example.powerai.data.util.AndroidNativeResourceProvider
    ): com.example.powerai.core.model.NativeResourceProvider

    @Binds
    @Singleton
    abstract fun bindHybridKbImportPort(impl: DocumentImportManager): HybridKbImportPort

    @Binds
    @Singleton
    abstract fun bindKnowledgeDetailSupportPort(impl: DatabaseUseCase): KnowledgeDetailSupportPort

    @Binds
    @Singleton
    abstract fun bindDatabaseKbPort(impl: AppDatabaseKbPort): DatabaseKbPort
}
