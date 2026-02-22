package com.example.powerai.di

import android.content.Context
import com.example.powerai.data.retriever.LocalFaissAnnRetriever
import com.example.powerai.data.retriever.HttpAnnRetriever
import com.example.powerai.domain.retriever.AnnRetriever
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FaissModule {

    @Provides
    @Singleton
    fun provideAnnRetriever(
        @ApplicationContext context: Context,
        local: LocalFaissAnnRetriever,
        http: HttpAnnRetriever
    ): AnnRetriever {
        val indexFile = context.filesDir.resolve("faiss/index.faiss")
        return if (indexFile.exists()) local else http
    }

}
