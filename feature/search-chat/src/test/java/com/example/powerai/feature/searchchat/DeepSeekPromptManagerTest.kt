package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.NativeResourceProvider
import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.domain.repository.WebSearchRepository
import com.example.powerai.domain.repository.WebSearchResult
import com.example.powerai.domain.retrieval.RetrievalFusionUseCase
import kotlinx.coroutines.test.runTest
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
    fun `web enabled and configured injects snippet into prompt`() =
        runTest {
            val retrieval = mock<RetrievalFusionUseCase>()
            whenever(retrieval.invoke(any(), any(), any())).thenReturn(listOf(localItem))
            val web = mock<WebSearchRepository>()
            whenever(web.isConfigured()).thenReturn(true)
            whenever(web.search(eq("接地电阻"), eq(5))).thenReturn(listOf(webHit))

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
                )

            assertTrue(prepared.prompt.contains("网页摘要关于接地电阻标准"))
            assertTrue(prepared.prompt.contains("[网1]"))
            assertTrue(prepared.prompt.contains("https://example.com/a"))
            assertTrue(prepared.webSources.contains("https://example.com/a"))
            assertTrue(prepared.references.single().id == 11L)
            verify(web).search("接地电阻", 5)
        }

    @Test
    fun `web disabled skips repository search`() =
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
                    webSearchEnabled = false,
                )

            assertFalse(prepared.prompt.contains("[网1]"))
            assertTrue(prepared.webSources.isEmpty())
            verify(web, never()).search(any(), any())
            verify(web, never()).isConfigured()
        }

    @Test
    fun `web enabled but not configured keeps local-only prompt`() =
        runTest {
            val retrieval = mock<RetrievalFusionUseCase>()
            whenever(retrieval.invoke(any(), any(), any())).thenReturn(listOf(localItem))
            val web = mock<WebSearchRepository>()
            whenever(web.isConfigured()).thenReturn(false)

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
                )

            assertFalse(prepared.prompt.contains("[网1]"))
            assertTrue(prepared.webSources.isEmpty())
            assertTrue(prepared.prompt.contains("本地条款内容关于接地电阻"))
            verify(web, never()).search(any(), any())
        }
}
