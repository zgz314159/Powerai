package com.example.powerai.data.importer

import com.example.powerai.core.data.dao.KnowledgeDao

import com.example.powerai.core.model.ImportedFile

import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.entity.KnowledgeListItemEntity
import com.example.powerai.core.data.entity.KnowledgeRowPayloadStat
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.InputStream

/**
 * In-memory implementation of KnowledgeDao for testing.
 */
class MemoryKnowledgeDao : KnowledgeDao {
    private val list = ArrayList<KnowledgeEntity>()

    override suspend fun insert(entity: KnowledgeEntity) {
        list.add(entity)
    }

    override suspend fun insertBatch(entities: List<KnowledgeEntity>) {
        list.addAll(entities)
    }

    override suspend fun upsertBatch(entities: List<KnowledgeEntity>) {
        val byId = list.associateBy { it.id }.toMutableMap()
        for (e in entities) byId[e.id] = e
        list.clear()
        list.addAll(byId.values)
    }

    override suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) {
        upsertBatch(entities)
    }

    override suspend fun getAll(): List<KnowledgeEntity> = list.toList()

    override suspend fun getAllForDatabaseList(): List<KnowledgeListItemEntity> = list.map {
        KnowledgeListItemEntity(
            id = it.id,
            title = it.title,
            content = it.content.take(4000),
            source = it.source,
            category = it.category,
            pageNumber = it.pageNumber,
            sortOrder = it.sortOrder,
            imageUris = it.imageUris
        )
    }

    override suspend fun getById(id: Long): KnowledgeEntity? = list.find { it.id == id }

    override suspend fun updateBlocksJsonAndSearchFields(
        id: Long,
        contentBlocksJson: String,
        contentNormalized: String,
        searchContent: String
    ) {
        updateEntityById(id) { e ->
            e.copy(
                contentBlocksJson = contentBlocksJson,
                contentNormalized = contentNormalized,
                searchContent = searchContent
            )
        }
    }

    override suspend fun getEntriesMissingNormalized(): List<KnowledgeEntity> =
        list.filter { it.contentNormalized.isEmpty() }

    override suspend fun countEntriesMissingNormalized(): Int =
        getEntriesMissingNormalized().size

    override suspend fun updateNormalizedContent(id: Long, normalized: String) {
        updateEntityById(id) { e ->
            e.copy(
                contentNormalized = normalized,
                searchContent = normalized
            )
        }
    }

    override suspend fun searchByKeyword(keyword: String): List<KnowledgeEntity> =
        list.filter { it.content.contains(keyword) || it.title.contains(keyword) || it.source.contains(keyword) }

    override suspend fun searchByKeywordInContent(keyword: String): List<KnowledgeEntity> =
        list.filter { it.searchContent.contains(keyword) || it.content.contains(keyword) }

    override suspend fun searchByKeywordInContentForDatabase(keyword: String): List<KnowledgeListItemEntity> =
        searchByKeywordInContent(keyword).map {
            KnowledgeListItemEntity(
                id = it.id,
                title = it.title,
                content = it.content.take(4000),
                source = it.source,
                category = it.category,
                pageNumber = it.pageNumber,
                sortOrder = it.sortOrder,
                imageUris = it.imageUris
            )
        }

    override suspend fun searchByLikeInternal(raw: String): List<KnowledgeEntity> =
        list.filter { it.content.contains(raw) || it.title.contains(raw) }.take(15)

    override suspend fun update(entity: KnowledgeEntity) {
        val idx = list.indexOfFirst { it.id == entity.id }
        if (idx >= 0) list[idx] = entity else list.add(entity)
    }

    override suspend fun searchByFts(query: String): List<KnowledgeEntity> =
        list.filter { it.contentNormalized.contains(query) }

    override suspend fun rebuildFts() {
        // no-op
    }

    override suspend fun countFts(): Int = list.size

    override suspend fun getSample(n: Int): List<KnowledgeEntity> = list.take(n)

    override suspend fun countBySourcePrefix(sourcePrefix: String): Int =
        list.count { it.source.startsWith(sourcePrefix) }

    override suspend fun countMatchesBySourcePrefix(sourcePrefix: String, keywordNoSpace: String): Int =
        list.count {
            it.source.startsWith(sourcePrefix) &&
                it.searchContent.replace(" ", "").contains(keywordNoSpace)
        }

    override suspend fun sampleBySourcePrefix(sourcePrefix: String, limit: Int): List<KnowledgeEntity> =
        list.filter { it.source.startsWith(sourcePrefix) }.take(limit)

    override suspend fun searchByKeywordNoSpace(keywordNoSpace: String): List<KnowledgeEntity> =
        list.filter {
            it.searchContent.replace(" ", "").contains(keywordNoSpace) ||
                it.title.replace(" ", "").contains(keywordNoSpace) ||
                it.source.replace(" ", "").contains(keywordNoSpace)
        }

    override suspend fun searchByKeywordNoSpaceInContent(keywordNoSpace: String): List<KnowledgeEntity> =
        list.filter { it.searchContent.contains(keywordNoSpace) }

    override suspend fun searchByKeywordNoSpaceInContentForDatabase(keywordNoSpace: String): List<KnowledgeListItemEntity> =
        searchByKeywordNoSpaceInContent(keywordNoSpace).map {
            KnowledgeListItemEntity(
                id = it.id,
                title = it.title,
                content = it.content.take(4000),
                source = it.source,
                category = it.category,
                pageNumber = it.pageNumber,
                sortOrder = it.sortOrder,
                imageUris = it.imageUris
            )
        }

    override suspend fun getLargestKnowledgeRows(limit: Int): List<KnowledgeRowPayloadStat> =
        list.map {
            KnowledgeRowPayloadStat(
                id = it.id,
                source = it.source,
                contentLength = it.content.length,
                contentBlocksLength = it.contentBlocksJson?.length ?: 0,
                bboxLength = it.bboxJson?.length ?: 0,
                imageUrisLength = it.imageUris?.length ?: 0
            )
        }.sortedByDescending { it.contentBlocksLength }.take(limit)

    override suspend fun searchByKeywordFuzzy(pattern: String): List<KnowledgeEntity> =
        list.filter { it.searchContent.contains(pattern) }

    override suspend fun insertImportedFile(file: ImportedFileEntity) {
        // no-op in memory
    }

    override suspend fun importedFileExists(fileId: String): Int = 0

    override suspend fun getImportedFileStatus(fileId: String): String? = null

    override suspend fun getImportedFiles(): List<ImportedFileEntity> = emptyList()

    override suspend fun countByPage(fileId: String, page: Int): Int =
        list.count { it.source.contains(fileId) && it.pageNumber == page }

    override suspend fun getByPage(fileId: String, page: Int): List<KnowledgeEntity> =
        list.filter { it.source.contains(fileId) && it.pageNumber == page }.sortedBy { it.id }

    private inline fun updateEntityById(id: Long, transform: (KnowledgeEntity) -> KnowledgeEntity) {
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = transform(list[idx])
        }
    }

    companion object {
        @JvmStatic
        fun importIntoMemoryDaoBlocking(
            inputStream: InputStream,
            batchSize: Int = ImportDefaults.DEFAULT_BATCH_SIZE,
            trace: ((String) -> Unit)? = null,
            fallbackFileName: String? = null,
            fallbackFileId: String? = null
        ): Pair<Long, Int> {
            val memory = MemoryKnowledgeDao()
            var importedSoFar = 0L
            runBlocking {
                val importer = JsonResourceImporter(memory)
                importer.importFromJson(
                    inputStream,
                    batchSize,
                    trace,
                    fallbackFileName,
                    fallbackFileId
                ).collect { p ->
                    importedSoFar = p.importedItems
                }
            }
            val fts = runBlocking { memory.countFts() }
            return importedSoFar to fts
        }
    }
}
