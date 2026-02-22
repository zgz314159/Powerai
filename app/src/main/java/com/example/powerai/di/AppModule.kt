package com.example.powerai.di

import android.content.Context
import androidx.room.Room
import com.example.powerai.BuildConfig
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.json.JsonRepository
import com.example.powerai.data.local.database.AppDatabase
import com.example.powerai.data.remote.api.AiApiService
import com.example.powerai.data.remote.api.VectorSearchApiService
import com.example.powerai.data.remote.dto.VectorSearchRequest
import com.example.powerai.data.remote.dto.VectorSearchResponse
import com.example.powerai.data.repository.KnowledgeRepositoryImpl
import com.example.powerai.domain.repository.KnowledgeRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFilesDir(@ApplicationContext context: Context): File = context.filesDir

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "powerai.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideKnowledgeDao(db: AppDatabase) = db.knowledgeDao()

    @Provides
    @Singleton
    fun provideVisionCacheDao(db: AppDatabase) = db.visionCacheDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideDocumentImportManager(
        @ApplicationContext context: Context,
        repo: com.example.powerai.domain.repository.KnowledgeRepository,
        dao: com.example.powerai.data.local.dao.KnowledgeDao,
        observability: com.example.powerai.util.ObservabilityService
    ): DocumentImportManager {
        return DocumentImportManager(context, repo, dao, observability)
    }

    @Provides
    @Singleton
    fun provideJsonRepository(@ApplicationContext context: Context): JsonRepository {
        return JsonRepository(context)
    }

    @Provides
    @Singleton
    fun provideKnowledgeRepositoryImpl(
        @ApplicationContext context: Context,
        dao: com.example.powerai.data.local.dao.KnowledgeDao,
        db: AppDatabase
    ): KnowledgeRepositoryImpl {
        // EmbeddingRepository will be injected to KnowledgeRepositoryImpl for enqueueing
        val embeddingRepo = com.example.powerai.data.repository.EmbeddingRepositoryImpl(context, db)
        return KnowledgeRepositoryImpl(context, dao, embeddingRepo)
    }

    @Provides
    @Singleton
    fun provideKnowledgeRepository(
        vectorRepo: com.example.powerai.data.repository.VectorSearchRepository
    ): com.example.powerai.domain.repository.KnowledgeRepository {
        return vectorRepo
    }

    @Provides
    @Singleton
    fun provideAiApiService(): AiApiService {
        val base = BuildConfig.AI_BASE_URL.trim()
        if (base.isBlank()) {
            return object : AiApiService {
                override suspend fun chatCompletions(request: com.example.powerai.data.remote.api.ChatCompletionsRequest): com.example.powerai.data.remote.api.ChatCompletionsResponse {
                    return com.example.powerai.data.remote.api.ChatCompletionsResponse(
                        choices = listOf(
                            com.example.powerai.data.remote.api.ChatChoice(
                                message = com.example.powerai.data.remote.api.ChatMessage(
                                    role = "assistant",
                                    content = "AI 未配置：请在本地通过 Gradle 配置 BuildConfig.AI_BASE_URL（以及可选的 AI_API_KEY）。"
                                )
                            )
                        )
                    )
                }
            }
        }

        val normalizedBaseUrl = if (base.endsWith("/")) base else "$base/"

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val apiKey = BuildConfig.AI_API_KEY.trim()
                val req0 = chain.request()
                val req = req0.newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .apply {
                        if (apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer $apiKey")
                        }
                    }
                    .build()
                chain.proceed(req)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideVectorSearchApiService(): VectorSearchApiService {
        return object : VectorSearchApiService {
            override suspend fun search(request: VectorSearchRequest): VectorSearchResponse {
                return VectorSearchResponse(results = emptyList())
            }
        }
    }

    @Provides
    @Singleton
    fun provideFontSettings(@ApplicationContext context: Context): com.example.powerai.data.settings.FontSettings {
        return com.example.powerai.data.settings.FontSettings(context)
    }

    @Provides
    @Singleton
    fun provideObservability(@ApplicationContext context: Context): com.example.powerai.util.ObservabilityService {
        return com.example.powerai.util.ObservabilityService(context)
    }

    @Provides
    @Singleton
    fun provideRetrievalFusionService(
        repo: com.example.powerai.domain.repository.KnowledgeRepository,
        observability: com.example.powerai.util.ObservabilityService
    ): com.example.powerai.domain.retrieval.RetrievalFusionService {
        return com.example.powerai.domain.retrieval.RetrievalFusionService(repo, observability)
    }

    @Provides
    @Singleton
    fun provideRetrievalFusionUseCase(
        svc: com.example.powerai.domain.retrieval.RetrievalFusionService
    ): com.example.powerai.domain.usecase.RetrievalFusionUseCase {
        return com.example.powerai.domain.usecase.RetrievalFusionUseCase(svc)
    }

    @Provides
    @Singleton
    fun provideLlmFactualityScorer(
        ai: com.example.powerai.data.remote.api.AiApiService,
        observability: com.example.powerai.util.ObservabilityService
    ): com.example.powerai.domain.ai.LlmFactualityScorer {
        return com.example.powerai.domain.ai.LlmFactualityScorer(ai, observability)
    }
}
