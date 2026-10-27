package com.example.powerai.core.data.repository

import android.content.Context
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.entity.EmbeddingMetadataEntity
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.repository.EmbeddingRepository
import com.example.powerai.core.repository.VectorRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val vectorRepository: VectorRepository
) : EmbeddingRepository {

    /**
     * Records embedding metadata as pending. There is no WorkManager batch path:
     * the legacy filesDir/embeddings/pending + EmbeddingWorker pipeline was retired (KI-58).
     * Lexical FTS remains the production retrieval path; vector upsert is via [storeEmbedding].
     */
    override suspend fun enqueueForEmbedding(items: List<KnowledgeItem>) {
        for (item in items) {
            db.embeddingDao().upsert(
                EmbeddingMetadataEntity(
                    id = item.id,
                    fileName = item.source,
                    status = "pending",
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun storeEmbedding(itemId: Long, embedding: FloatArray) {
        vectorRepository.upsert(longArrayOf(itemId), embedding)
    }
}
