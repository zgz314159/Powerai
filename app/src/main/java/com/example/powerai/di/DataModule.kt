package com.example.powerai.di

import android.content.Context
import com.example.powerai.data.importer.AssetImportScanner
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.json.JsonRepository
import com.example.powerai.core.data.dao.EmbeddingDao
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.dao.VisionCacheDao
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.repository.VectorRepository
import com.example.powerai.core.model.ObservabilityService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAssetImportScanner(@ApplicationContext context: Context, dao: KnowledgeDao): AssetImportScanner {
        return AssetImportScanner(context, dao)
    }

    @Suppress("LongParameterList")
    @Provides
    @Singleton
    fun provideDocumentImportManager(
        @ApplicationContext context: Context,
        repo: KnowledgeRepository,
        dao: KnowledgeDao,
        scanner: AssetImportScanner,
        observability: ObservabilityService,
        visionCacheDao: VisionCacheDao,
        embeddingDao: EmbeddingDao,
        vectorRepository: VectorRepository,
        @Named("vector_index_path") vectorIndexPath: String,
    ): DocumentImportManager {
        return DocumentImportManager(
            context = context,
            repo = repo,
            dao = dao,
            scanner = scanner,
            observability = observability,
            visionCacheDao = visionCacheDao,
            embeddingDao = embeddingDao,
            vectorRepository = vectorRepository,
            vectorIndexPath = vectorIndexPath,
        )
    }

    @Provides
    @Singleton
    fun provideJsonRepository(@ApplicationContext context: Context): JsonRepository {
        return JsonRepository(context)
    }
}
