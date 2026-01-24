package com.example.powerai.di

import android.content.Context
import androidx.room.Room
import com.example.powerai.data.local.database.AppDatabase
import com.example.powerai.data.repository.KnowledgeRepositoryImpl
import com.example.powerai.domain.repository.KnowledgeRepository
import com.example.powerai.domain.usecase.LocalSearchUseCase
import com.example.powerai.data.remote.api.AiApiService
import com.example.powerai.data.json.JsonRepository
import com.example.powerai.data.importer.DocumentImportManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
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
    fun provideDocumentImportManager(
        @ApplicationContext context: Context,
        repo: com.example.powerai.domain.repository.KnowledgeRepository
    ): DocumentImportManager {
        return DocumentImportManager(context, repo)
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
        dao: com.example.powerai.data.local.dao.KnowledgeDao
    ): KnowledgeRepositoryImpl {
        return KnowledgeRepositoryImpl(context, dao)
    }

    @Provides
    @Singleton
    fun provideKnowledgeRepository(
        impl: KnowledgeRepositoryImpl
    ): com.example.powerai.domain.repository.KnowledgeRepository {
        return impl
    }



    @Provides
    @Singleton
    fun provideAiApiService(): AiApiService {
        return Retrofit.Builder()
            .baseUrl("https://example.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideFontSettings(@ApplicationContext context: Context): com.example.powerai.data.settings.FontSettings {
        return com.example.powerai.data.settings.FontSettings(context)
    }
}
