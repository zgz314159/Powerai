package com.example.powerai.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.repository.EmbeddingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import android.util.Log
import com.example.powerai.data.retriever.NativeVectorRepository
import javax.inject.Singleton

/**
 * Embedding repository implementation that enqueues items for background embedding via WorkManager
 * and persists embeddings as binary files under `filesDir/embeddings/`.
 *
 * Behavior:
 * - `enqueueForEmbedding` writes pending JSON files under `filesDir/embeddings/pending` and
 *   triggers a `EmbeddingWorker` OneTimeWorkRequest (unique enqueue).
 * - `storeEmbedding` writes binary embedding file `{id}.emb` and updates a small metadata JSON.
 */
@Singleton
class EmbeddingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: com.example.powerai.data.local.database.AppDatabase
) : EmbeddingRepository {
    // optional native index repo; injected via AppModule.set after construction
    private var nativeVectorRepository: NativeVectorRepository? = null

    fun setNativeVectorRepository(repo: NativeVectorRepository) {
        this.nativeVectorRepository = repo
    }
    private val baseDir: File by lazy { File(context.filesDir, "embeddings").apply { mkdirs() } }
    private val pendingDir: File by lazy { File(baseDir, "pending").apply { mkdirs() } }
    private val embeddingDao by lazy { db.embeddingDao() }

    override suspend fun enqueueForEmbedding(items: List<KnowledgeItem>) {
        withContext(Dispatchers.IO) {
            for (it in items) {
                val idPart = (it.id?.toString()) ?: ("tmp_${System.currentTimeMillis()}")
                val metaFile = File(pendingDir, "${idPart}.json")
                if (!metaFile.exists()) {
                    val payload = buildString {
                        append('{')
                        append("\"id\":\"").append(idPart).append('\"')
                        append(',')
                        append("\"title\":\"").append(escape(it.title)).append('\"')
                        append(',')
                        append("\"content\":\"").append(escape(it.content)).append('\"')
                        append('}')
                    }
                    metaFile.writeText(payload)
                }
            }

            // Schedule a single background worker to process pending items.
            try {
                val work = OneTimeWorkRequestBuilder<com.example.powerai.data.worker.EmbeddingWorker>()
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork("powerai_embedding_worker", ExistingWorkPolicy.KEEP, work)
            } catch (_: Throwable) {
            }
        }
    }

    override suspend fun storeEmbedding(itemId: Long, embedding: FloatArray) {
        withContext(Dispatchers.IO) {
            try {
                val bin = File(baseDir, "${itemId}.emb")
                val fos = bin.outputStream()
                val bb = java.nio.ByteBuffer.allocate(4 * embedding.size)
                bb.asFloatBuffer().put(embedding)
                fos.write(bb.array())
                fos.close()

                val metaFile = File(baseDir, "${itemId}.json")
                val json = "{\"id\":${itemId},\"status\":\"done\"}"
                metaFile.writeText(json)

                // persist metadata into Room table as optional persistence
                try {
                    val meta = com.example.powerai.data.local.entity.EmbeddingMetadataEntity(
                        id = itemId,
                        fileName = bin.name,
                        status = "done",
                        createdAt = System.currentTimeMillis()
                    )
                    // launch coroutine safe call
                    kotlinx.coroutines.runBlocking {
                        embeddingDao.upsert(meta)
                    }

                    // Best-effort: immediately upsert into native index so searches can see it.
                    try {
                        nativeVectorRepository?.let { repo ->
                            val ok = repo.upsert(longArrayOf(itemId), embedding)
                            if (!ok) {
                                Log.w("EmbeddingRepo", "native upsert returned false for id=$itemId")
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w("EmbeddingRepo", "native upsert failed for id=$itemId", e)
                    }
                } catch (_: Throwable) {
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n","\\n")
    }
}
