package com.example.powerai.data.importer

import android.content.Context
import com.example.powerai.core.data.dao.EmbeddingDao
import com.example.powerai.core.data.dao.VisionCacheDao
import com.example.powerai.core.repository.VectorRepository
import java.io.File

/**
 * Drop the id-keyed caches (vision cache, embedding metadata) of a set of knowledge rows, leaving
 * every other package's entries intact. Used when a package is replaced or removed so no cached
 * entry survives a row that no longer exists.
 */
internal suspend fun invalidateEntityCachesFor(
    visionCacheDao: VisionCacheDao?,
    embeddingDao: EmbeddingDao?,
    entityIds: Collection<Long>,
) {
    if (entityIds.isEmpty()) return
    val ids = entityIds.toList()
    visionCacheDao?.deleteForEntities(ids)
    embeddingDao?.deleteByIds(ids)
}

/**
 * Clear the single native vector index — in memory and on disk — so ids that no longer have a
 * knowledge row are not returned by `VectorRepository.search` in the same process (no restart).
 * The index is a derived cache, so dropping it is always safe. Returns false when the persisted
 * index still exists afterwards (deletion failed), so the caller must not report a complete success.
 */
internal fun invalidateNativeVectorIndex(
    context: Context,
    vectorRepository: VectorRepository?,
    vectorIndexPath: String,
): Boolean {
    vectorRepository?.clear()
    val file = File(context.filesDir, vectorIndexPath)
    return !file.exists() || file.delete()
}
