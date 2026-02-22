package com.example.powerai.data.importer

import android.content.Context
import android.net.Uri
import com.example.powerai.data.knowledge.KnowledgeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FileImportManager: high-level helper that imports a SAF Uri using
 * the underlying KnowledgeRepository import flow and exposes a simple
 * Flow<Int> of progress percent values.
 */
@Singleton
class FileImportManager @Inject constructor(private val repo: KnowledgeRepository) {

    /**
     * Import a document via SAF Uri. Returns a Flow emitting percent complete (0-100).
     * The implementation delegates to KnowledgeRepository.importUriFlow which
     * performs streaming parsing, sanitization and atomic JSON writes.
     */
    suspend fun importFile(uri: Uri, context: Context, batchSize: Int = com.example.powerai.data.importer.ImportDefaults.DEFAULT_BATCH_SIZE): Flow<Int> {
        val displayName = uri.lastPathSegment ?: "imported"
        return repo.importUriFlow(uri, context.contentResolver, displayName, batchSize).map { prog ->
            // normalize percent to 0..100
            prog.percent.coerceIn(0, 100)
        }
    }
}
