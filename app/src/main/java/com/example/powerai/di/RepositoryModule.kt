package com.example.powerai.di

import com.example.powerai.data.vision.VisionBoostRepositoryImpl
import com.example.powerai.domain.vision.VisionBoostRepository
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
}
