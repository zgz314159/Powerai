package com.example.powerai.di

import android.content.Context
import com.example.powerai.AppConfig
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.repository.AnnRetriever
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.repository.RemoteConfigRepository
import com.example.powerai.core.model.ObservabilityService
import com.example.powerai.data.remote.api.AiApiService
import com.example.powerai.data.repository.RoomFtsRetriever
import com.example.powerai.domain.ai.LlmFactualityScorer
import com.example.powerai.domain.retrieval.HybridRetrievalService
import com.example.powerai.domain.retrieval.RetrievalFusionService
import com.example.powerai.engine.ai.GemmaLocalInference
import com.example.powerai.engine.ai.SparseSearcher
import com.example.powerai.feature.searchchat.RetrievalFusionUseCase
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
    fun provideSparseSearcher(@ApplicationContext context: Context): SparseSearcher {
        return SparseSearcher(context)
    }

    @Provides
    @Singleton
    fun provideGemmaLocalInference(@ApplicationContext context: Context): GemmaLocalInference {
        return GemmaLocalInference(context)
    }

    @Provides
    @Singleton
    fun provideLlmFactualityScorer(
        ai: com.example.powerai.core.repository.AiServiceRepository,
        config: RemoteConfigRepository,
        observability: ObservabilityService
    ): LlmFactualityScorer {
        return LlmFactualityScorer(ai, config, observability)
    }

    @Provides
    @Singleton
    fun provideRetrievalFusionService(
        repo: KnowledgeRepository,
        observability: ObservabilityService,
        annRetriever: AnnRetriever
    ): RetrievalFusionService {
        return RetrievalFusionService(repo, observability, annRetriever)
    }

    @Provides
    @Singleton
    fun provideHybridRetrievalService(
        annRetriever: AnnRetriever,
        dao: KnowledgeDao,
        @Named("retrieval_source_weights") sourceWeights: Map<String, Double>,
        @Named("fts_top_bonus") ftsTopBonus: Double
    ): HybridRetrievalService {
        val fts = RoomFtsRetriever(dao)
        return HybridRetrievalService(
            annRetriever,
            fts,
            rrfK = 60,
            sourceWeights = sourceWeights,
            ftsTopBonus = ftsTopBonus
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
        sparseSearcher: SparseSearcher
    ): RetrievalFusionUseCase {
        return RetrievalFusionUseCase(svc, sparseSearcher)
    }
}
