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
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object FaissModule {

    @Provides
    @Singleton
    fun provideAnnRetriever(
        @ApplicationContext context: Context,
        native: com.example.powerai.data.retriever.NativeAnnRetriever,
        local: LocalFaissAnnRetriever,
        http: HttpAnnRetriever,
        @Named("vector_index_path") vectorIndexPath: String
    ): AnnRetriever {
        // Prefer native ANN retriever by default (SMART mode favors native search).
        // Fall back to local Faiss or remote HTTP retriever if native is not appropriate.
        return try {
            native
        } catch (_: Throwable) {
            try {
                local
            } catch (_: Throwable) {
                http
            }
        }
    }

}
