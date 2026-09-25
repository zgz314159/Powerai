package com.example.powerai.engine.ai.di

import com.example.powerai.core.repository.RemoteConfigRepository
import com.example.powerai.engine.ai.AiApiService
import com.example.powerai.engine.ai.ApiChatCompletionsRequest
import com.example.powerai.engine.ai.ApiChatCompletionsResponse
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiNetworkModule {

    @Provides
    @Singleton
    fun provideAiApiService(config: RemoteConfigRepository): AiApiService {
        val base = config.getAiBaseUrl().trim()
        if (base.isBlank()) {
            return object : AiApiService {
                override suspend fun chatCompletions(request: ApiChatCompletionsRequest): ApiChatCompletionsResponse {
                    return ApiChatCompletionsResponse(choices = emptyList())
                }
            }
        }

        val normalizedBaseUrl = if (base.endsWith("/")) base else "$base/"
        val apiKey = config.getAiApiKey().trim()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
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
}
