package com.example.powerai.di

import android.content.Context
import com.example.powerai.data.importer.AssetImportScanner
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.json.JsonRepository
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.model.ObservabilityService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAssetImportScanner(@ApplicationContext context: Context, dao: KnowledgeDao): AssetImportScanner {
        return AssetImportScanner(context, dao)
    }

    @Provides
    @Singleton
    fun provideDocumentImportManager(
        @ApplicationContext context: Context,
        repo: KnowledgeRepository,
        dao: KnowledgeDao,
        scanner: AssetImportScanner,
        observability: ObservabilityService
    ): DocumentImportManager {
        return DocumentImportManager(context, repo, dao, scanner, observability)
    }

    @Provides
    @Singleton
    fun provideJsonRepository(@ApplicationContext context: Context): JsonRepository {
        return JsonRepository(context)
    }
}
