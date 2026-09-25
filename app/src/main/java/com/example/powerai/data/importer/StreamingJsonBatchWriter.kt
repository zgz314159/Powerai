package com.example.powerai.data.importer

import com.example.powerai.core.data.dao.KnowledgeDao

import com.example.powerai.core.data.entity.KnowledgeEntity

/**
 * Helper that accumulates [EntityBuilder] instances and writes them to the DAO
 * in batches.  Inspired by the previous logic in
 * [StreamingJsonResourceImporter.importFromJson].
 *
 * The caller invokes [add] for each builder and may optionally call [flush]
 * when all elements are done.  A callback can receive the number written so
 * far in a batch so that progress calculations remain external to this class.
 */
internal class StreamingJsonBatchWriter(
    private val dao: KnowledgeDao,
    private val batchSize: Int,
    private val trace: ((String) -> Unit)? = null
) {
    private val batchBuilders = ArrayList<EntityBuilder>(batchSize.coerceAtLeast(16))
    private val capacity = batchSize.coerceAtLeast(16)
    private val reusableEntitiesArray = arrayOfNulls<KnowledgeEntity>(capacity)
    private var reusableEntitiesSize = 0
    private val reusableEntitiesList = object : AbstractList<KnowledgeEntity>() {
        override fun get(index: Int): KnowledgeEntity = reusableEntitiesArray[index]!!
        override val size: Int
            get() = reusableEntitiesSize
    }

    /**
     * Add a newly populated builder. If the batch threshold is reached, the
     * contents will be flushed to the DAO automatically. Returns the number of
     * entities written during the flush (0 if none).
     */
    internal suspend fun add(builder: EntityBuilder): Int {
        batchBuilders.add(builder)
        if (batchBuilders.size >= batchSize) {
            return flushInternal()
        }
        return 0
    }

    /**
     * Alternative for callers that already have a [KnowledgeEntity] instead of a
     * builder.  This avoids the need to allocate a temporary builder object.
     */
    internal suspend fun addEntity(entity: KnowledgeEntity): Int {
        // reuse the same internal array logic without resetting a builder
        if (reusableEntitiesArray.size < batchBuilders.size + 1) {
            throw IllegalStateException("reusableEntitiesArray too small")
        }
        batchBuilders.add(EntityBuilder().apply { 
            // temporarily wrap entity for flushInternal use
            // but flushInternal converts builder.toEntity() so we simply reset later
            val e = entity
            this.id = e.id
            this.title = e.title
            this.content = e.content
            this.contentNormalized = e.contentNormalized
            this.searchContent = e.searchContent
            this.source = e.source
            this.contentBlocksJson = e.contentBlocksJson
            this.pageNumber = e.pageNumber
            this.bboxJson = e.bboxJson
            this.imageUris = e.imageUris
            this.category = e.category
            this.keywordsSerialized = e.keywordsSerialized
        })
        if (batchBuilders.size >= batchSize) {
            return flushInternal()
        }
        return 0
    }

    /**
     * Flush any remaining builders. Returns number written (may be 0).
     */
    internal suspend fun flush(): Int {
        if (batchBuilders.isEmpty()) return 0
        return flushInternal()
    }

    private suspend fun flushInternal(): Int {
        if (reusableEntitiesArray.size < batchBuilders.size) {
            throw IllegalStateException("reusableEntitiesArray too small")
        }
        var i = 0
        for (b in batchBuilders) {
            reusableEntitiesArray[i++] = b.toEntity()
        }
        reusableEntitiesSize = batchBuilders.size
        dao.upsertBatchTransactional(reusableEntitiesList)
        val written = reusableEntitiesSize
        try { trace?.invoke("StreamingJsonBatchWriter: wrote batch size=$written") } catch (_: Throwable) {}
        // clear out
        for (j in 0 until reusableEntitiesSize) reusableEntitiesArray[j] = null
        for (b in batchBuilders) b.reset()
        batchBuilders.clear()
        return written
    }
}
