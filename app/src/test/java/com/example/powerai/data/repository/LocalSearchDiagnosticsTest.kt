package com.example.powerai.data.repository

import com.example.powerai.BuildConfig
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.entity.KnowledgeListItemEntity
import com.example.powerai.core.data.entity.KnowledgeRowPayloadStat
import com.example.powerai.core.data.repository.LocalSearchDiagnostics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalSearchDiagnosticsTest {
    private class CountingDao : KnowledgeDao {
        var countFtsCalls = 0
        override suspend fun countFts(): Int { countFtsCalls++; return 42 }
        override suspend fun insert(entity: KnowledgeEntity) {}
        override suspend fun insertBatch(entities: List<KnowledgeEntity>) {}
        override suspend fun upsertBatch(entities: List<KnowledgeEntity>) {}
        override suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) {}
        override suspend fun getAll(): List<KnowledgeEntity> = emptyList()
        override suspend fun getAllForDatabaseList(): List<KnowledgeListItemEntity> = emptyList()
        override suspend fun getById(id: Long): KnowledgeEntity? = null
        override suspend fun updateBlocksJsonAndSearchFields(id: Long, contentBlocksJson: String, contentNormalized: String, searchContent: String) {}
        override suspend fun getEntriesMissingNormalized(): List<KnowledgeEntity> = emptyList()
        override suspend fun countEntriesMissingNormalized(): Int = 0
        override suspend fun updateNormalizedContent(id: Long, normalized: String) {}
        override suspend fun searchByKeyword(keyword: String): List<KnowledgeEntity> = emptyList()
        override suspend fun searchByKeywordInContent(keyword: String): List<KnowledgeEntity> = emptyList()
        override suspend fun searchByKeywordInContentForDatabase(keyword: String) = emptyList<KnowledgeListItemEntity>()
        override suspend fun searchByLikeInternal(raw: String): List<KnowledgeEntity> = emptyList()
        override suspend fun update(entity: KnowledgeEntity) {}
        override suspend fun searchByFts(query: String): List<KnowledgeEntity> = emptyList()
        override suspend fun rebuildFts() {}
        override suspend fun getSample(n: Int): List<KnowledgeEntity> = emptyList()
        override suspend fun countBySourcePrefix(sourcePrefix: String): Int = 0
        override suspend fun countMatchesBySourcePrefix(sourcePrefix: String, keywordNoSpace: String): Int = 0
        override suspend fun sampleBySourcePrefix(sourcePrefix: String, limit: Int): List<KnowledgeEntity> = emptyList()
        override suspend fun searchByKeywordNoSpace(keywordNoSpace: String): List<KnowledgeEntity> = emptyList()
        override suspend fun searchByKeywordNoSpaceInContent(keywordNoSpace: String): List<KnowledgeEntity> = emptyList()
        override suspend fun searchByKeywordNoSpaceInContentForDatabase(keywordNoSpace: String) = emptyList<KnowledgeListItemEntity>()
        override suspend fun searchByKeywordFuzzy(pattern: String): List<KnowledgeEntity> = emptyList()
        override suspend fun getLargestKnowledgeRows(limit: Int) = emptyList<KnowledgeRowPayloadStat>()
        override suspend fun insertImportedFile(file: ImportedFileEntity) {}
        override suspend fun importedFileExists(fileId: String): Int = 0
        override suspend fun getImportedFileStatus(fileId: String): String? = null
        override suspend fun getImportedFiles(): List<ImportedFileEntity> = emptyList()
        override suspend fun countByPage(fileId: String, page: Int): Int = 0
        override suspend fun getByPage(fileId: String, page: Int): List<KnowledgeEntity> = emptyList()
    }

    private val fakeDao = CountingDao()

    @Before
    fun setUp() {
        LocalSearchDiagnostics.resetForTests()
    }

    @Test
    fun `shouldEmitSnippetDiagnostics returns true first time then false for same query`() {
        val q = "hello"
        val first = LocalSearchDiagnostics.shouldEmitSnippetDiagnostics(q, hasCjk = false)
        val second = LocalSearchDiagnostics.shouldEmitSnippetDiagnostics(q, hasCjk = false)
        val third = LocalSearchDiagnostics.shouldEmitSnippetDiagnostics("world", hasCjk = true)

        if (BuildConfig.DEBUG) {
            assertTrue(first)
            assertFalse(second)
            assertTrue(third)
        } else {
            assertFalse(first)
            assertFalse(second)
            assertFalse(third)
        }
    }

    @Test
    fun `logInitialFtsCount only executes once`() {
        kotlinx.coroutines.runBlocking { LocalSearchDiagnostics.logInitialFtsCount(fakeDao) }
        kotlinx.coroutines.runBlocking { LocalSearchDiagnostics.logInitialFtsCount(fakeDao) }
        if (BuildConfig.DEBUG) {
            assertTrue(fakeDao.countFtsCalls == 1)
        } else {
            assertTrue(fakeDao.countFtsCalls == 0)
        }
    }
}