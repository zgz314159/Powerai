package com.example.powerai.data.repository

import com.example.powerai.core.repository.EmbeddingRepository

import com.example.powerai.core.model.KnowledgeItem

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Helper responsible for file-based persistence and worker scheduling used by
 * [EmbeddingRepositoryImpl].
 *
 * Splitting these responsibilities makes the repository itself thin and
 * enables focused unit testing of the I/O behavior without needing the full
 * database or native-index dependencies.
 */
class EmbeddingIoHelper @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val context: Context,
    private val baseDir: File = File(context.filesDir, "embeddings").apply { mkdirs() },
    private val workManager: WorkManager = WorkManager.getInstance(context)
) {
    private val pendingDir: File = File(baseDir, "pending").apply { mkdirs() }

    /**
     * Ensure the directory structure exists. Useful for tests.
     */
    fun ensureDirs() {
        if (!baseDir.exists()) baseDir.mkdirs()
        if (!pendingDir.exists()) pendingDir.mkdirs()
    }

    /**
     * Write JSON metadata files for the given items and enqueue a worker.
     */
    fun enqueueForEmbedding(items: List<KnowledgeItem>) {
        for (it in items) {
            val idPart = (it.id?.toString()) ?: ("tmp_${System.currentTimeMillis()}")
            val metaFile = File(pendingDir, "${idPart}.json")
            if (!metaFile.exists()) {
                val payload = buildString {
                    append('{')
                    append("\"id\":\"").append(idPart).append('"')
                    append(',')
                    append("\"title\":\"").append(escape(it.title)).append('"')
                    append(',')
                    append("\"content\":\"").append(escape(it.content)).append('"')
                    append('}')
                }
                metaFile.writeText(payload)
            }
        }

        // schedule worker; ignore failures so caller remains robust
        try {
            val work: OneTimeWorkRequest = OneTimeWorkRequestBuilder<com.example.powerai.data.worker.EmbeddingWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(
                "powerai_embedding_worker",
                ExistingWorkPolicy.KEEP,
                work
            )
        } catch (_: Throwable) {
        }
    }

    /**
     * Persist a single embedding to disk (binary and JSON) without touching
     * the database or native index. Returns the file written or null on failure.
     */
    fun storeEmbeddingFile(itemId: Long, embedding: FloatArray): File? {
        return try {
            val bin = File(baseDir, "${itemId}.emb")
            val bb = java.nio.ByteBuffer.allocate(4 * embedding.size)
            bb.asFloatBuffer().put(embedding)
            bin.writeBytes(bb.array())

            val metaFile = File(baseDir, "${itemId}.json")
            val json = "{\"id\":${itemId},\"status\":\"done\"}"
            metaFile.writeText(json)
            bin
        } catch (_: Throwable) {
            null
        }
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
