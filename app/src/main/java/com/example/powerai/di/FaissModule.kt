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
        // Prefer native index if we have a persisted native index present.
        val nativeIndex = context.filesDir.resolve(vectorIndexPath)
        val faissIndex = context.filesDir.resolve("faiss/index.faiss")
        return when {
            nativeIndex.exists() -> native
            faissIndex.exists() -> local
            else -> http
        }
    }

}
