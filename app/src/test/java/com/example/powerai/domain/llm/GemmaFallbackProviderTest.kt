package com.example.powerai.domain.llm

import com.example.powerai.domain.generator.PromptBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.Mockito.mock
import com.example.powerai.engine.ai.SparseSearcher
import com.example.powerai.engine.ai.GemmaFallbackProvider

class GemmaFallbackProviderTest {

    @Test
    fun `infer returns result with title using provided searcher`() = runBlocking {
        val fakeSearcher = mock(SparseSearcher::class.java)
        val entry = SparseSearcher.SafetyEntry(title = "Example", content = "c", source = "src")
        whenever(fakeSearcher.search(any<String>(), any())).thenReturn(listOf(entry))

        val provider = GemmaFallbackProvider(null)
        val result = provider.infer(PromptBuilder.Prompt(system = "", user = "test")) { fakeSearcher }

        assertTrue(result.text.contains("规程建议"))
        assertTrue(result.text.contains("Example"))
        assertTrue(result.tokensUsed > 0)
    }

    @Test
    fun `infer returns generic message when searcher yields nothing`() = runBlocking {
        val fakeSearcher = mock(SparseSearcher::class.java)
        whenever(fakeSearcher.search(any<String>(), any())).thenReturn(emptyList())

        val provider = GemmaFallbackProvider(null)
        val result = provider.infer(PromptBuilder.Prompt(system = "", user = "")) { fakeSearcher }

        assertTrue(result.text.contains("规程建议"))
        assertTrue(result.text.contains("相关规程"))
    }
}
