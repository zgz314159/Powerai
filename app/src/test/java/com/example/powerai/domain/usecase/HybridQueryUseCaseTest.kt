package com.example.powerai.domain.usecase

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.engine.ai.GemmaLocalInference
import com.example.powerai.feature.searchchat.RetrievalFusionUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HybridQueryUseCaseTest {
    private lateinit var localSearch: RetrievalFusionUseCase
    private lateinit var askAi: AskAiUseCase
    private lateinit var gemma: GemmaLocalInference
    private lateinit var knowledgeRepository: KnowledgeRepository
    private lateinit var useCase: HybridQueryUseCase

    @Before
    fun setup() {
        localSearch = mock()
        askAi = mock()
        gemma = mock()
        knowledgeRepository = mock()
        useCase = HybridQueryUseCase(localSearch, askAi, gemma, knowledgeRepository)
    }

    @Test
    fun `localMode returns wrapped results and items`() = runBlocking {
        val item = KnowledgeItem(
            id = 123L,
            title = "变压器检修要点",
            content = "变压器检修时应先切断负荷，变压器检修步骤如下。",
            source = "s",
            pageNumber = null,
            category = "",
            keywords = emptyList(),
            hitBlockIndex = null,
            hitBlockId = null
        )
        val rr = RetrievalResult(item = item, source = "local", score = 0.5f)
        whenever(localSearch.invokeResults("变压器检修")).thenReturn(listOf(rr))

        val outcome = useCase.localMode("变压器检修", "变压器检修", this) {}
        outcome.gemmaJob?.join()
        assertEquals(1, outcome.query.retrievals.size)
        assertEquals(item.id, outcome.query.retrievals[0].item!!.id)
        assertEquals(item.title, outcome.query.retrievals[0].item!!.title)
        assertEquals("local", outcome.query.retrievals[0].source)
        assertEquals(1, outcome.query.items.size)
        assertEquals(item.id, outcome.query.items[0].id)
        assertEquals(item.title, outcome.query.items[0].title)
    }

    @Test
    fun `localMode handles empty fallback`() = runBlocking {
        whenever(localSearch.invokeResults("x")).thenThrow(RuntimeException("fail"))
        val outcome = useCase.localMode("x", "x", this) {}
        outcome.gemmaJob?.join()
        assertTrue(outcome.query.retrievals.isEmpty())
        assertTrue(outcome.query.items.isEmpty())
    }

    @Test
    fun `aiMode invokes AskAiUseCase and propagates answer`() = runBlocking {
        whenever(askAi.invokeAiSearch("h", webSearchEnabled = true)).thenReturn("hello")
        val answer = useCase.aiMode("h", webSearchEnabled = true)
        assertEquals("hello", answer)
    }

    @Test
    fun `aiMode catches exception`() = runBlocking {
        whenever(askAi.invokeAiSearch(any(), any())).thenThrow(RuntimeException("boom"))
        val answer = useCase.aiMode("q", webSearchEnabled = false)
        assertTrue(answer.startsWith("AI error:"))
    }

    @Test
    fun `invoke returns QueryResult with computed confidence`() = runBlocking {
        val item1 = KnowledgeItem(1, "t", "c", "s", null, "", emptyList(), null, null)
        val item2 = KnowledgeItem(2, "t2", "c2", "s2", null, "", emptyList(), null, null)
        whenever(localSearch.invoke("q", limit = 10, forceAnn = true)).thenReturn(listOf(item1, item2))
        whenever(askAi.invoke(any(), any())).thenReturn("resp")

        val result = useCase.invoke("q")
        assertEquals("resp", result.answer)
        assertEquals(listOf(item1, item2), result.references)
        assertTrue(result.confidence >= 0.3f && result.confidence <= 1f)
    }

    @Test
    fun `hybridQuery appends ai entry when few local results`() = runBlocking {
        whenever(localSearch.invoke("k")).thenReturn(listOf())
        whenever(askAi.invoke(any(), any())).thenReturn("aires")
        val list = useCase.hybridQuery("k")
        assertTrue(list.size == 1)
        assertTrue(list[0].status == "ai")
        assertTrue(list[0].content.contains("aires"))
    }

    @Test
    fun `hybridQuery does not append when enough local results`() = runBlocking {
        val items = (1..3).map {
            KnowledgeItem(it.toLong(), "t", "c", "s", null, "", emptyList(), null, null)
        }
        whenever(localSearch.invoke("k")).thenReturn(items)
        val list = useCase.hybridQuery("k")
        assertEquals(3, list.size)
        assertFalse(list.any { it.status == "ai" })
    }
}
