package com.example.powerai.data.repository

import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.example.powerai.core.data.repository.KnowledgeLocalSearchQuery
import com.example.powerai.core.data.repository.CjkSearchStrategy
import com.example.powerai.core.data.repository.GenericSearchStrategy

class KnowledgeLocalSearchStrategiesTest {

    private fun makeEntity(id: Long) = KnowledgeEntity(
        id = id,
        title = "",
        content = "",
        source = "",
        category = "",
        keywordsSerialized = "",
        contentNormalized = "",
        searchContent = "",
        pageNumber = null,
        contentBlocksJson = null,
        bboxJson = null,
        imageUris = null,
        vectorChecksum = "",
        indexedAt = 0L
    )

    @Test
    fun cjkStrategy_returnsLikeResults() = runBlocking {
        val dao = mock<KnowledgeDao>()
        // when CJK, query builder returns hasCjk true
        whenever(dao.searchByKeywordNoSpace(any())).thenReturn(listOf(makeEntity(1)))

        val built = KnowledgeLocalSearchQuery(raw = "x", normalized = "x", noSpace = "x", hasCjk = true)
        val results = CjkSearchStrategy.search(dao, built)

        assertEquals(1, results.size)
        assertEquals(1L, results.first().id)
    }

    @Test
    fun genericStrategy_mergesFtsAndLikeAndFuzzy() = runBlocking {
        val dao = mock<KnowledgeDao>()
        // configure FTS to return one entity
        whenever(dao.searchByFts(any())).thenReturn(listOf(makeEntity(2)))
        // configure LIKE internal to return overlapping entity
        whenever(dao.searchByLikeInternal(any())).thenReturn(listOf(makeEntity(2), makeEntity(3)))
        // fuzzy should not be called because merged nonempty

        val built = KnowledgeLocalSearchQuery(raw = "q", normalized = "q", noSpace = "q", hasCjk = false)
        val results = GenericSearchStrategy.search(dao, built)

        assertEquals(2, results.size)
        val ids = results.map { it.id }
        assertTrue(ids.containsAll(listOf(2L, 3L)))
    }
}