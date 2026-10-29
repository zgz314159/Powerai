package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.domain.repository.WebSearchRepository
import com.example.powerai.domain.repository.WebSearchResult
import com.example.powerai.domain.retrieval.RetrievalFusionUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DeepSeekPromptManagerTest {
    private val localItem =
        KnowledgeItem(
            id = 11L,
            title = "本地条目标题",
            content = "本地条款内容关于接地电阻",
            source = "local.pdf",
            category = "安规",
            keywords = emptyList(),
        )

    private val webHit =
        WebSearchResult(
            title = "网页标题",
            url = "https://example.com/a",
            snippet = "网页摘要关于接地电阻标准",
        )

    @Test
    fun `strict prompt and device date are always injected`() =
        runTest {
            val retrieval = mock<RetrievalFusionUseCase>()
            whenever(retrieval.invoke(any(), any(), any())).thenReturn(listOf(localItem))
            val manager = DeepSeekPromptManager(retrieval, mock())
            val prepared =
                manager.prepareGroundedPrompt(
                    normalizedQuestion = "接地电阻",
                    answerMode = SmartAnswerMode.FAST,
                    prefixReuseEnabled = false,
                    backendLabel = "CPU",
                    provider = mock<NativeResourceProvider>(),
                    onProgress = { _, _, _ -> },
                    webSearchEnabled = false,
                    deviceToday = "2026-10-29",
                )

            assertTrue(prepared.prompt.contains("今天日期：2026-10-29"))
            assertTrue(prepared.prompt.contains("资料不足，无法回答"))
            assertTrue(prepared.prompt.contains("只依据参考资料"))
            assertFalse(prepared.prompt.contains("常识兜底"))
            assertEquals(SmartQueryKind.DOMAIN, prepared.queryKind)
        }

    @Test
    fun `domain with local hits does not fetch web even when enabled`() =
        runTest {
            val retrieval = mock<RetrievalFusionUseCase>()
            whenever(retrieval.invoke(any(), any(), any())).thenReturn(listOf(localItem))
            val web = mock<WebSearchRepository>()

            val manager = DeepSeekPromptManager(retrieval, web)
            val prepared =
                manager.prepareGroundedPrompt(
                    normalizedQuestion = "接地电阻",
                    answerMode = SmartAnswerMode.FAST,
                    prefixReuseEnabled = false,
                    backendLabel = "CPU",
                    provider = mock<NativeResourceProvider>(),
                    onProgress = { _, _, _ -> },
                    webSearchEnabled = true,
                    deviceToday = "2026-10-29",
                )

            assertFalse(prepared.prompt.contains("[网1]"))
            assertTrue(prepared.webSources.isEmpty())
            verify(web, never()).search(any(), any())
            verify(web, never()).isConfigured()
        }

    @Test
    fun `temporal with web enabled fetches short web snippets`() =
        runTest {
            val retrieval = mock<RetrievalFusionUseCase>()
            whenever(retrieval.invoke(any(), any(), any())).thenReturn(emptyList())
            val web = mock<WebSearchRepository>()
            whenever(web.isConfigured()).thenReturn(true)
            whenever(web.search(eq("今天几号？"), eq(3))).thenReturn(listOf(webHit))

            val manager = DeepSeekPromptManager(retrieval, web)
            val prepared =
                manager.prepareGroundedPrompt(
                    normalizedQuestion = "今天几号？",
                    answerMode = SmartAnswerMode.FAST,
                    prefixReuseEnabled = false,
                    backendLabel = "CPU",
                    provider = mock<NativeResourceProvider>(),
                    onProgress = { _, _, _ -> },
                    webSearchEnabled = true,
                    deviceToday = "2026-10-29",
                )

            assertEquals(SmartQueryKind.TEMPORAL, prepared.queryKind)
            assertTrue(prepared.prompt.contains("今天日期：2026-10-29"))
            assertTrue(prepared.prompt.contains("网页摘要关于接地电阻标准"))
            assertTrue(prepared.prompt.contains("[网1]"))
            assertTrue(prepared.webSources.contains("https://example.com/a"))
            verify(web).search("今天几号？", 3)
        }

    @Test
    fun `web disabled skips repository search even for temporal`() =
        runTest {
            val retrieval = mock<RetrievalFusionUseCase>()
            whenever(retrieval.invoke(any(), any(), any())).thenReturn(emptyList())
            val web = mock<WebSearchRepository>()

            val manager = DeepSeekPromptManager(retrieval, web)
            val prepared =
                manager.prepareGroundedPrompt(
                    normalizedQuestion = "今天几号？",
                    answerMode = SmartAnswerMode.FAST,
                    prefixReuseEnabled = false,
                    backendLabel = "CPU",
                    provider = mock<NativeResourceProvider>(),
                    onProgress = { _, _, _ -> },
                    webSearchEnabled = false,
                    deviceToday = "2026-10-29",
                )

            assertTrue(prepared.prompt.contains("今天日期：2026-10-29"))
            assertFalse(prepared.prompt.contains("[网1]"))
            assertTrue(prepared.webSources.isEmpty())
            verify(web, never()).search(any(), any())
            verify(web, never()).isConfigured()
        }

    @Test
    fun `domain zero hit with web enabled fetches web`() =
        runTest {
            val retrieval = mock<RetrievalFusionUseCase>()
            whenever(retrieval.invoke(any(), any(), any())).thenReturn(emptyList())
            val web = mock<WebSearchRepository>()
            whenever(web.isConfigured()).thenReturn(true)
            whenever(web.search(eq("接地电阻"), eq(3))).thenReturn(listOf(webHit))

            val manager = DeepSeekPromptManager(retrieval, web)
            val prepared =
                manager.prepareGroundedPrompt(
                    normalizedQuestion = "接地电阻",
                    answerMode = SmartAnswerMode.FAST,
                    prefixReuseEnabled = false,
                    backendLabel = "CPU",
                    provider = mock<NativeResourceProvider>(),
                    onProgress = { _, _, _ -> },
                    webSearchEnabled = true,
                    deviceToday = "2026-10-29",
                )

            assertTrue(prepared.prompt.contains("[网1]"))
            verify(web).search("接地电阻", 3)
        }

    @Test
    fun `local snippet is truncated for prefill budget`() =
        runTest {
            val long =
                localItem.copy(content = "甲".repeat(2000))
            val retrieval = mock<RetrievalFusionUseCase>()
            whenever(retrieval.invoke(any(), any(), any())).thenReturn(listOf(long))
            val manager = DeepSeekPromptManager(retrieval, mock())
            val prepared =
                manager.prepareGroundedPrompt(
                    normalizedQuestion = "接地电阻",
                    answerMode = SmartAnswerMode.FAST,
                    prefixReuseEnabled = false,
                    backendLabel = "CPU",
                    provider = mock<NativeResourceProvider>(),
                    onProgress = { _, _, _ -> },
                    webSearchEnabled = false,
                    deviceToday = "2026-10-29",
                )

            assertTrue(prepared.prompt.contains("…"))
            assertTrue(prepared.prompt.length < 2000)
        }
}
