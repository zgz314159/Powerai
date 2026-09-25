package com.example.powerai.data.importer

import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.entity.KnowledgeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StreamingJsonBatchWriterTest {
    private class DummyDao : KnowledgeDao {
        val written = mutableListOf<KnowledgeEntity>()
        override suspend fun insert(entity: KnowledgeEntity) {}
        override suspend fun insertBatch(entities: List<KnowledgeEntity>) {}
        override suspend fun upsertBatch(entities: List<KnowledgeEntity>) { written.addAll(entities) }
        override suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) { written.addAll(entities) }
        override suspend fun getAll(): List<KnowledgeEntity> = emptyList()
        override suspend fun getAllForDatabaseList() = emptyList<com.example.powerai.core.data.entity.KnowledgeListItemEntity>()
        override suspend fun getById(id: Long): KnowledgeEntity? = null
        override suspend fun updateBlocksJsonAndSearchFields(id: Long, contentBlocksJson: String, contentNormalized: String, searchContent: String) {}
        override suspend fun getEntriesMissingNormalized(): List<KnowledgeEntity> = emptyList()
        override suspend fun countEntriesMissingNormalized(): Int = 0
        override suspend fun updateNormalizedContent(id: Long, normalized: String) {}
        override suspend fun searchByKeyword(keyword: String): List<KnowledgeEntity> = emptyList()
        override suspend fun searchByKeywordInContent(keyword: String): List<KnowledgeEntity> = emptyList()
        override suspend fun searchByKeywordInContentForDatabase(keyword: String) = emptyList<com.example.powerai.core.data.entity.KnowledgeListItemEntity>()
        override suspend fun searchByLikeInternal(raw: String): List<KnowledgeEntity> = emptyList()
        override suspend fun update(entity: KnowledgeEntity) {}
        override suspend fun searchByFts(query: String): List<KnowledgeEntity> = emptyList()
        override suspend fun rebuildFts() {}
        override suspend fun countFts(): Int = 0
        override suspend fun getSample(n: Int): List<KnowledgeEntity> = emptyList()
        override suspend fun countBySourcePrefix(sourcePrefix: String): Int = 0
        override suspend fun countMatchesBySourcePrefix(sourcePrefix: String, keywordNoSpace: String): Int = 0
        override suspend fun sampleBySourcePrefix(sourcePrefix: String, limit: Int): List<KnowledgeEntity> = emptyList()
        override suspend fun searchByKeywordNoSpace(keywordNoSpace: String): List<KnowledgeEntity> = emptyList()
        override suspend fun searchByKeywordNoSpaceInContent(keywordNoSpace: String): List<KnowledgeEntity> = emptyList()
        override suspend fun searchByKeywordNoSpaceInContentForDatabase(keywordNoSpace: String) = emptyList<com.example.powerai.core.data.entity.KnowledgeListItemEntity>()
        override suspend fun searchByKeywordFuzzy(pattern: String): List<KnowledgeEntity> = emptyList()
        override suspend fun getLargestKnowledgeRows(limit: Int) = emptyList<com.example.powerai.core.data.entity.KnowledgeRowPayloadStat>()
        override suspend fun insertImportedFile(file: ImportedFileEntity) {}
        override suspend fun importedFileExists(fileId: String): Int = 0
        override suspend fun getImportedFileStatus(fileId: String): String? = null
        override suspend fun getImportedFiles(): List<ImportedFileEntity> = emptyList()
        override suspend fun countByPage(fileId: String, page: Int): Int = 0
        override suspend fun getByPage(fileId: String, page: Int): List<KnowledgeEntity> = emptyList()
    }

    @Test
    fun `batch writer flushes and resets builders`() = runBlocking {
        val dao = DummyDao()
        val writer = StreamingJsonBatchWriter(dao, batchSize = 2, trace = { /*no-op*/ })

        val b1 = EntityBuilder().apply { id = 1 }
        val b2 = EntityBuilder().apply { id = 2 }
        val b3 = EntityBuilder().apply { id = 3 }

        val w1 = writer.add(b1)
        assertEquals(0, w1)
        val w2 = writer.add(b2)
        assertEquals(2, w2)
        assertEquals("", b1.title)
        assertEquals("", b2.title)

        val w3 = writer.add(b3)
        assertEquals(0, w3)
        val w4 = writer.flush()
        assertEquals(1, w4)
        assertEquals(3, dao.written.size)
    }
}
