package com.example.powerai.domain.usecase

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.domain.retrieval.HybridRetrievalService
import com.example.powerai.domain.retrieval.RetrievalFusionUseCase
import com.example.powerai.engine.ai.SparseSearcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class RetrievalFusionUseCaseTest {
    private lateinit var hybridService: HybridRetrievalService
    private lateinit var sparseSearcher: SparseSearcher
    private lateinit var knowledgeRepository: KnowledgeRepository
    private lateinit var useCase: RetrievalFusionUseCase

    @Before
    fun setup() {
        hybridService = mock(HybridRetrievalService::class.java)
        sparseSearcher = mock(SparseSearcher::class.java)
        knowledgeRepository = mock(KnowledgeRepository::class.java)
        useCase = RetrievalFusionUseCase(hybridService, sparseSearcher, knowledgeRepository)
        whenever(sparseSearcher.search(any(), any<Int>())).thenReturn(emptyList())
        runBlocking {
            whenever(hybridService.retrieveHybrid(any(), any<Int>())).thenReturn(emptyList())
            whenever(knowledgeRepository.findByExactTitle(any())).thenReturn(null)
            whenever(knowledgeRepository.searchByKeyword(any())).thenReturn(emptyList())
        }
    }

    @Test
    fun `invokeResults merges rewritten fact-query retrievals and promotes stable hit`() =
        runBlocking {
            whenever(hybridService.retrieveHybrid(eq("变压器在什么情况下停止运行"), eq(10))).thenReturn(
                listOf(retrieval(id = 1L, title = "背景说明", content = "一般性背景。", score = 0.12f)),
            )
            whenever(hybridService.retrieveHybrid(eq("变压器 停止运行"), eq(10))).thenReturn(
                listOf(
                    retrieval(
                        id = 2L,
                        title = "第三十条 运行变压器有下列情况之一时，应立即停止运行：",
                        content = "运行变压器有下列情况之一时，应立即停止运行。",
                        score = 0.56f,
                    ),
                ),
            )
            whenever(hybridService.retrieveHybrid(eq("变压器 停止运行 条件"), eq(10))).thenReturn(
                listOf(
                    retrieval(
                        id = 2L,
                        title = "第三十条 运行变压器有下列情况之一时，应立即停止运行：",
                        content = "运行变压器有下列情况之一时，应立即停止运行。",
                        score = 0.52f,
                    ),
                ),
            )
            val results = useCase.invokeResults("变压器在什么情况下停止运行")

            assertEquals(2L, results.first().id)
            assertTrue((results.first().confidence ?: 0f) > 0.56f)
            assertEquals(2, results.first().debug?.get("query_variant_hit_count"))
        }

    @Test
    fun `invokeResults uses definition query plan and records plan strategy`() =
        runBlocking {
            whenever(hybridService.retrieveHybrid(eq("隔离开关用途"), eq(10))).thenReturn(emptyList())
            whenever(hybridService.retrieveHybrid(eq("隔离开关 的主要用途是什么"), eq(10))).thenReturn(
                listOf(
                    retrieval(
                        id = 158L,
                        title = "158.隔离开关的主要用途是什么？",
                        content = "答：一是检修与分段隔离，二是倒换母线，三是分、合空载电路。",
                        score = 0.54f,
                    ),
                ),
            )
            whenever(hybridService.retrieveHybrid(eq("隔离开关"), eq(10))).thenReturn(
                listOf(retrieval(id = 201L, title = "第60条 隔离开关操作顺序", content = "送电操作顺序与此相反。", score = 0.40f)),
            )
            whenever(hybridService.retrieveHybrid(eq("隔离开关 答"), eq(10))).thenReturn(
                listOf(
                    retrieval(
                        id = 158L,
                        title = "158.隔离开关的主要用途是什么？",
                        content = "答：一是检修与分段隔离，二是倒换母线，三是分、合空载电路。",
                        score = 0.51f,
                    ),
                ),
            )

            val results = useCase.invokeResults("隔离开关用途")

            assertEquals(158L, results.first().id)
            assertEquals(2, results.first().debug?.get("query_variant_hit_count"))
            assertTrue(results.first().debug?.containsKey("query_plan_strategy") == true)
        }

    @Test
    fun `invoke resolves sparse hit to Room id by exact title`() =
        runBlocking {
            whenever(hybridService.retrieveHybrid(any(), any<Int>())).thenReturn(emptyList())
            whenever(sparseSearcher.search(any(), any<Int>())).thenReturn(
                listOf(
                    SparseSearcher.SafetyEntry(
                        "10kV验电",
                        "10kV验电可使用手持式验电器或验电笔，操作人员应穿戴绝缘鞋和绝缘手套。",
                        "电力安全工作规程",
                    ),
                ),
            )
            whenever(knowledgeRepository.findByExactTitle("10kV验电")).thenReturn(
                KnowledgeItem(
                    id = 4242L,
                    title = "10kV验电",
                    content = "10kV验电可使用手持式验电器或验电笔，操作人员应穿戴绝缘鞋和绝缘手套。",
                    source = "电力安全工作规程",
                    category = "安规",
                    keywords = emptyList(),
                ),
            )

            val items = useCase.invoke("10KV如何验电", limit = 5)
            assertEquals(1, items.size)
            assertEquals(4242L, items.first().id)
            assertEquals("电力安全工作规程", items.first().source)
        }

    private fun retrieval(
        id: Long,
        title: String,
        content: String,
        score: Float,
    ): RetrievalResult {
        return RetrievalResult(
            id = id,
            score = score,
            confidence = score,
            source = "fts",
            item =
                KnowledgeItem(
                    id = id,
                    title = title,
                    content = content,
                    source = "rule.pdf",
                    pageNumber = null,
                    category = "",
                    keywords = emptyList(),
                    hitBlockIndex = null,
                    hitBlockId = null,
                ),
        )
    }
}
