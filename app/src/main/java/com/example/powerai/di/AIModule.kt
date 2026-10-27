package com.example.powerai.di

import android.content.Context
import com.example.powerai.AppConfig
import com.example.powerai.core.repository.AnnRetriever
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.domain.retrieval.FtsRetriever
import com.example.powerai.domain.retrieval.HybridRetrievalService
import com.example.powerai.domain.retrieval.RetrievalFusionUseCase
import com.example.powerai.domain.usecase.LocalHybridQueryOrchestrator
import com.example.powerai.engine.ai.GemmaLocalInference
import com.example.powerai.engine.ai.SparseSearcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIModule {
    @Provides
    @Singleton
    @Named("vector_dim")
    fun provideVectorDim(): Int = AppConfig.VECTOR_DIM

    @Provides
    @Singleton
    @Named("vector_index_path")
    fun provideVectorIndexPath(): String = "vector_index.bin"

    @Provides
    @Singleton
    fun provideSparseSearcher(
        @ApplicationContext context: Context,
    ): SparseSearcher {
        return SparseSearcher(context)
    }

    @Provides
    @Singleton
    fun provideGemmaLocalInference(
        @ApplicationContext context: Context,
    ): GemmaLocalInference {
        return GemmaLocalInference(context)
    }

    @Provides
    @Singleton
    fun provideHybridRetrievalService(
        annRetriever: AnnRetriever,
        ftsRetriever: FtsRetriever,
        @Named("retrieval_source_weights") sourceWeights: Map<String, Double>,
        @Named("fts_top_bonus") ftsTopBonus: Double,
    ): HybridRetrievalService {
        return HybridRetrievalService(
            annRetriever,
            ftsRetriever,
            rrfK = 60,
            sourceWeights = sourceWeights,
            ftsTopBonus = ftsTopBonus,
        )
    }

    @Provides
    @Singleton
    @Named("retrieval_source_weights")
    fun provideRetrievalSourceWeights(): Map<String, Double> {
        return mapOf("native" to 1.0, "ann" to 1.0, "fts" to 2.0)
    }

    @Provides
    @Singleton
    @Named("fts_top_bonus")
    fun provideFtsTopBonus(): Double {
        return 0.06
    }

    @Provides
    @Singleton
    fun provideRetrievalFusionUseCase(
        svc: HybridRetrievalService,
        sparseSearcher: SparseSearcher,
    ): RetrievalFusionUseCase {
        return RetrievalFusionUseCase(svc, sparseSearcher)
    }

    @Provides
    @Singleton
    fun provideLocalHybridQueryOrchestrator(
        fusion: RetrievalFusionUseCase,
        knowledgeRepository: KnowledgeRepository,
    ): LocalHybridQueryOrchestrator {
        return LocalHybridQueryOrchestrator(fusion, knowledgeRepository)
    }
}
