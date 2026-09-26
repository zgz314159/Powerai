package com.example.powerai.di

import android.content.Context
import com.example.powerai.BuildConfig
import com.example.powerai.data.remote.api.VectorSearchApiService
import com.example.powerai.data.remote.dto.VectorSearchRequest
import com.example.powerai.data.remote.dto.VectorSearchResponse
import com.example.powerai.data.retriever.AnnApiService
import com.example.powerai.data.retriever.AnnSearchRequest
import com.example.powerai.data.retriever.AnnSearchResponse
import com.example.powerai.engine.ai.AiApiService
import com.example.powerai.engine.ai.ApiChatChoice
import com.example.powerai.engine.ai.ApiChatCompletionsRequest
import com.example.powerai.engine.ai.ApiChatCompletionsResponse
import com.example.powerai.engine.ai.ApiChatMessage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 网络模块：提HTTP 客户端和 API 服务依赖
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("visionValidation")
    fun provideVisionValidationAiApiService(): AiApiService {
        val base = BuildConfig.AI_BASE_URL.trim()
        if (base.isBlank()) {
            val message =
                ApiChatMessage(
                    role = "assistant",
                    content = "AI 未配置：请在本地通过 Gradle 配置 BuildConfig.AI_BASE_URL（以及可选的 AI_API_KEY）",
                )
            val choice = ApiChatChoice(message = message)
            return object : AiApiService {
                override suspend fun chatCompletions(request: ApiChatCompletionsRequest): ApiChatCompletionsResponse {
                    return ApiChatCompletionsResponse(choices = listOf(choice))
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
    fun provideAnnApiService(@ApplicationContext context: Context): AnnApiService {
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
            return object : AnnApiService {
                override suspend fun search(req: AnnSearchRequest): AnnSearchResponse {
                    return AnnSearchResponse(results = emptyList())
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
            .create(AnnApiService::class.java)
    }
}
