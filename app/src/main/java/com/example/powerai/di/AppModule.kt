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
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFilesDir(@ApplicationContext context: Context): File = context.filesDir

    @Provides
    @Singleton
    @Named("vector_dim")
    fun provideVectorDim(): Int = 128

    @Provides
    @Singleton
    @Named("vector_index_path")
    fun provideVectorIndexPath(): String = "vector_index.bin"

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
            .apply {
                if (BuildConfig.DEBUG) {
                    val logger = HttpLoggingInterceptor()
                    logger.level = HttpLoggingInterceptor.Level.BODY
                    addInterceptor(logger)
                }
            }
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

    @Provides
    @Singleton
    fun provideAnnApiService(@ApplicationContext context: Context): com.example.powerai.data.retriever.AnnApiService {
        var base = BuildConfig.AI_BASE_URL.trim()

        // If BuildConfig doesn't provide a URL (common during debug), allow overriding
        // by placing a plaintext file `ai_base_url.txt` in the app's files dir containing
        // the base URL (e.g. http://192.168.1.100:8000/). This makes it easy to point
        // a physical device to your host without rebuilding.
        if (base.isBlank()) {
            try {
                val cfg = context.filesDir.resolve("ai_base_url.txt")
                if (cfg.exists()) {
                    base = cfg.readText().trim()
                }
            } catch (_: Exception) {
                // ignore and fallthrough to empty check
            }
        }

        if (base.isBlank()) {
            return object : com.example.powerai.data.retriever.AnnApiService {
                override suspend fun search(req: com.example.powerai.data.retriever.AnnSearchRequest): com.example.powerai.data.retriever.AnnSearchResponse {
                    return com.example.powerai.data.retriever.AnnSearchResponse(results = emptyList())
                }
            }
        }

        val normalizedBaseUrl = if (base.endsWith("/")) base else "$base/"

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    val logger = HttpLoggingInterceptor()
                    logger.level = HttpLoggingInterceptor.Level.BODY
                    addInterceptor(logger)
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.example.powerai.data.retriever.AnnApiService::class.java)
    }
}
