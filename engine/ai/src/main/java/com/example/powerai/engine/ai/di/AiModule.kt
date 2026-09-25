package com.example.powerai.engine.ai.di

import com.example.powerai.engine.ai.AiServiceRepositoryImpl
import com.example.powerai.engine.ai.AiStreamingRepositoryImpl
import com.example.powerai.core.repository.AiServiceRepository
import com.example.powerai.core.repository.AiStreamingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindAiServiceRepository(
        aiServiceRepositoryImpl: AiServiceRepositoryImpl
    ): AiServiceRepository

    @Binds
    @Singleton
    abstract fun bindAiStreamingRepository(
        aiStreamingRepositoryImpl: AiStreamingRepositoryImpl
    ): AiStreamingRepository
}
