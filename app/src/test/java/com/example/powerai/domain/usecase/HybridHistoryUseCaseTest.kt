package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.SearchEntry
import com.example.powerai.domain.repository.HistoryScope
import com.example.powerai.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

private class FakeSearchHistoryRepository(
    initialLocal: List<SearchEntry> = emptyList(),
    initialSmart: List<SearchEntry> = emptyList()
) : SearchHistoryRepository {
    private val data = mutableMapOf(
        HistoryScope.LOCAL to initialLocal.toMutableList(),
        HistoryScope.SMART to initialSmart.toMutableList(),
        HistoryScope.DATABASE to mutableListOf<SearchEntry>()
    )

    override suspend fun loadHistory(scope: HistoryScope): List<SearchEntry> =
        data[scope]?.toList() ?: emptyList()

    override suspend fun saveHistory(scope: HistoryScope, history: List<SearchEntry>) {
        data[scope] = history.toMutableList()
    }

    override suspend fun clearHistory(scope: HistoryScope) {
        data[scope]?.clear()
    }
}

class HybridHistoryUseCaseTest {
    private fun makeCase(
        initialLocal: List<SearchEntry> = emptyList(),
        initialSmart: List<SearchEntry> = emptyList()
    ): HybridHistoryUseCase = HybridHistoryUseCase(
        FakeSearchHistoryRepository(initialLocal, initialSmart)
    )

    @Test
    fun `init loads persisted lists`() = runBlocking {
        val localEntries = listOf(SearchEntry("a", 1))
        val smartEntries = listOf(SearchEntry("b", 2))
        val case = makeCase(localEntries, smartEntries)
        case.init()
        assertEquals(localEntries, case.localHistory.value)
        assertEquals(smartEntries, case.smartHistory.value)
    }

    @Test
    fun `addLocal inserts and dedupes`() = runBlocking {
        val case = makeCase()
        case.init()
        case.addLocal("foo")
        assertEquals(1, case.localHistory.value.size)
        assertEquals("foo", case.localHistory.value[0].query)
        case.addLocal("foo")
        assertEquals(1, case.localHistory.value.size)
        case.addLocal("bar")
        assertEquals(listOf("bar", "foo"), case.localHistory.value.map { it.query })
    }

    @Test
    fun `clearLocal empties`() = runBlocking {
        val case = makeCase(listOf(SearchEntry("x", 10)))
        case.init()
        assertTrue(case.localHistory.value.isNotEmpty())
        case.clearLocal()
        assertTrue(case.localHistory.value.isEmpty())
    }

    @Test
    fun `addSmart and clearSmart behave similarly`() = runBlocking {
        val case = makeCase()
        case.init()
        case.addSmart("s1")
        assertEquals(1, case.smartHistory.value.size)
        case.addSmart("s1")
        assertEquals(1, case.smartHistory.value.size)
        case.clearSmart()
        assertTrue(case.smartHistory.value.isEmpty())
    }
}
