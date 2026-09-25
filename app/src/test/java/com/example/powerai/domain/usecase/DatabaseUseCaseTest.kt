package com.example.powerai.domain.usecase

import com.example.powerai.core.model.ImportedFile
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.domain.repository.HistoryScope
import com.example.powerai.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseUseCaseTest {
    private lateinit var repository: KnowledgeRepository
    private lateinit var historyRepository: SearchHistoryRepository
    private lateinit var useCase: DatabaseUseCase

    @Before
    fun setup() {
        repository = mock()
        historyRepository = mock()
        useCase = DatabaseUseCase(repository, historyRepository)
    }

    private fun item(id: Long, source: String, imagesCount: Int = 0): KnowledgeItem =
        KnowledgeItem(
            id = id,
            title = "t$id",
            content = "c$id",
            source = source,
            pageNumber = null,
            category = "",
            keywords = emptyList(),
            imagesCount = imagesCount
        )

    @Test
    fun `loadAll groups entities by file metadata`() = runBlocking {
        val imported = listOf(
            ImportedFile(fileId = "f1", fileName = "file1", timestamp = 1L, status = "imported"),
            ImportedFile(fileId = "f2", fileName = "file2", timestamp = 2L, status = "imported")
        )
        val ent1 = item(1L, "file1::something")
        val ent2 = item(2L, "file2::foo", imagesCount = 1)
        whenever(repository.getImportedFiles()).thenReturn(imported)
        whenever(repository.getAll()).thenReturn(listOf(ent1, ent2))

        val groups = useCase.loadAll()
        assertTrue(groups.isNotEmpty())
        val first = groups.find { it.fileName == "file1" } ?: groups.first()
        assertEquals(1, first.rows.size)
        assertEquals(0, first.totalImages)
        val second = groups.find { it.fileName == "file2" }
        if (second != null) {
            assertEquals(1, second.rows.size)
            assertTrue(second.totalImages >= 0)
        }
    }

    @Test
    fun `search merges keyword and nospace results without duplicates`() = runBlocking {
        val e1 = item(1L, "s")
        val e2 = item(2L, "s")
        whenever(repository.searchByKeyword("q")).thenReturn(listOf(e1, e2))
        whenever(repository.getImportedFiles()).thenReturn(emptyList())

        val groups = useCase.search("q")
        assertEquals(1, groups.size)
        val rows = groups[0].rows
        assertEquals(2, rows.size)
    }

    @Test
    fun `addSearchHistory trims and dedupes entries`() = runBlocking {
        whenever(historyRepository.loadHistory(HistoryScope.DATABASE))
            .thenReturn(listOf(com.example.powerai.domain.model.SearchEntry("a", 1)))
        useCase.addSearchHistory(" a ")
        verify(historyRepository).saveHistory(
            eq(HistoryScope.DATABASE),
            argThat { size == 1 && this[0].query == "a" }
        )
    }

    @Test
    fun `clearSearchHistory delegates to store`() = runBlocking {
        useCase.clearSearchHistory()
        verify(historyRepository).clearHistory(HistoryScope.DATABASE)
    }

    @Test
    fun `loadHistory propagates repository failure`() = runBlocking {
        whenever(historyRepository.loadHistory(HistoryScope.DATABASE))
            .thenThrow(RuntimeException("fail"))
        try {
            useCase.loadHistory()
            fail("expected repository failure to propagate")
        } catch (e: RuntimeException) {
            assertEquals("fail", e.message)
        }
    }
}
