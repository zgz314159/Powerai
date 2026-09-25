package com.example.powerai.domain.llm

import android.content.Context
import com.example.powerai.domain.generator.PromptBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.Mockito.*
import com.example.powerai.engine.ai.SparseSearcher
import com.example.powerai.engine.ai.GemmaLocalInference
import com.example.powerai.engine.ai.GemmaFallbackProvider
import com.example.powerai.engine.ai.GemmaInferenceEngine
import com.example.powerai.engine.ai.GemmaModelLoaderType

class GemmaLocalInferenceTest {
    class FakeLoader(var toReturn: Any? = null) : GemmaModelLoaderType {
        override fun tryLoad(context: Context?): Any? = toReturn
        override fun closeEngine(engine: Any?, context: Context?) { /* no-op */ }
    }

    @Test
    fun `loadModel returns false when loader returns null`() {
        val loader = FakeLoader(null)
        val service = GemmaLocalInference(context = null, loader = loader)
        assertFalse(service.loadModel())
    }

    @Test
    fun `loadModel returns true when loader succeeds`() {
        val loader = FakeLoader("engine")
        val service = GemmaLocalInference(context = null, loader = loader)
        assertTrue(service.loadModel())
    }

    @Test
    fun `infer returns fallback when engine not available`() = runBlocking {
        val service = GemmaLocalInference(context = null)
        // ensure model not loaded
        // call infer with simple prompt
        val prompt = PromptBuilder.Prompt(system = "", user = "test")
        val result = service.infer(prompt, maxTokens = 10)
        assertNotNull(result)
        assertTrue(
            "Fallback text should mention 本地模型加载中 or AI 正在思考",
            result.text.contains("本地模型加载中") || result.text.contains("AI 正在思考")
        )
        // tokensUsed should be positive
        assertTrue(result.tokensUsed > 0)
    }

    // formatting logic moved to GemmaInferenceEngine; tests are covered there.

    @Test
    fun `fallbackInference uses searcher and returns result with title`() = runBlocking {
        val fakeSearcher = mock(SparseSearcher::class.java)
        val entry = SparseSearcher.SafetyEntry(title = "Title", content = "c", source = "File")
        whenever(fakeSearcher.search(anyString(), anyInt())).thenReturn(listOf(entry))

        // call provider directly with custom factory
        val provider = GemmaFallbackProvider(null)
        val result = provider.infer(
            PromptBuilder.Prompt(system = "", user = "foo")
        ) { fakeSearcher }

        assertTrue(result.text.contains("规程建议"))
        assertTrue(result.text.contains("Title"))
    }

    @Test
    fun `fallbackInference returns generic when no search results`() = runBlocking {
        val fakeSearcher = mock(SparseSearcher::class.java)
        whenever(fakeSearcher.search(anyString(), anyInt())).thenReturn(emptyList())

        val provider = GemmaFallbackProvider(null)
        val result = provider.infer(
            PromptBuilder.Prompt(system = "", user = "")
        ) { fakeSearcher }

        assertTrue(result.text.contains("规程建议"))
        assertTrue(result.text.contains("相关规程"))
    }

    @Test
    fun `close should clear engine and loaded flag`() {
        val inf = GemmaLocalInference(null)
        // set private fields via reflection
        val engField = inf.javaClass.getDeclaredField("engine").apply { isAccessible = true }
        val loadField = inf.javaClass.getDeclaredField("loaded").apply { isAccessible = true }
        engField.set(inf, "dummy")
        loadField.setBoolean(inf, true)

        inf.close()

        assertNull(engField.get(inf))
        assertFalse(loadField.getBoolean(inf))
    }
}
