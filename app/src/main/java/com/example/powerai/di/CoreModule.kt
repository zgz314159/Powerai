package com.example.powerai.di

import android.content.Context
import androidx.work.WorkManager
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

/**
 * 核心模块：提供基础设施依赖（Context、Gson、WorkManager、Dispatcher 等）
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideFilesDir(@ApplicationContext context: Context): File = context.filesDir

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    @Named("io")
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideObservability(@ApplicationContext context: Context): com.example.powerai.core.model.ObservabilityService {
        return com.example.powerai.core.data.util.ObservabilityServiceImpl(context)
    }

    @Provides
    @Singleton
    fun provideFontSettings(@ApplicationContext context: Context): com.example.powerai.data.settings.FontSettings {
        return com.example.powerai.data.settings.FontSettings(context)
    }
}
