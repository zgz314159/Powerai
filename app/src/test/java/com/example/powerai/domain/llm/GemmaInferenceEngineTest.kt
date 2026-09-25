package com.example.powerai.domain.llm

import com.example.powerai.domain.generator.PromptBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.powerai.engine.ai.SparseSearcher
import com.example.powerai.engine.ai.GemmaLocalInference
import com.example.powerai.engine.ai.GemmaInferenceEngine

class GemmaInferenceEngineTest {
    @Test
    fun `formatRAGPrompt produces expected structure`() {
        val entries = listOf(
            SparseSearcher.SafetyEntry(title = "t", content = "c", source = "s")
        )
        val formatted = GemmaInferenceEngine.formatRAGPrompt(entries, "问答")
        assert(formatted.contains("参考1: c"))
        assert(formatted.contains("问题: 问答"))
    }

    @Test
    fun `formatRAGPrompt handles empty entries and truncation`() {
        val longQuery = "Q".repeat(2000)
        val formatted = GemmaInferenceEngine.formatRAGPrompt(emptyList(), longQuery)
        assert(formatted.startsWith("参考: 暂无相关规程记录"))
        // ensure it doesn't crash even with very long query
        assert(formatted.contains("问题: "))
    }
    // simple fake engine exposing a "generate" method similar to MediaPipe
    class FakeEngine {
        fun generate(prompt: String, options: Map<String, Any>): String {
            return "fake-$prompt"
        }
    }

    @Test
    fun `engine infer returns stub result`() = runBlocking {
        val prompt = PromptBuilder.Prompt(system = "", user = "hi")
        val result = GemmaInferenceEngine.infer(context = null, engine = FakeEngine(), prompt = prompt, maxTokens = 10)
        println("engine infer result: '${result.text}' tokens=${result.tokensUsed}")
        if (!result.text.startsWith("fake-")) {
            org.junit.Assert.fail("expected response to start with fake- but was ${result.text}")
        }
        // logger should at least compute a positive token count
        assert(result.tokensUsed > 0)
    }

    @Test
    fun `local inference delegates to engine`() = runBlocking {
        val prompt = PromptBuilder.Prompt(system = "", user = "world")
        val local = GemmaLocalInference(null)
        // cheat by injecting stub engine and marking as loaded
        val engineField = GemmaLocalInference::class.java.getDeclaredField("engine")
        engineField.isAccessible = true
        engineField.set(local, FakeEngine())
        val loadedField = GemmaLocalInference::class.java.getDeclaredField("loaded")
        loadedField.isAccessible = true
        loadedField.setBoolean(local, true)

        val result = local.infer(prompt, 10)
        println("local infer result: '${result.text}'")
        if (!result.text.startsWith("fake-")) {
            org.junit.Assert.fail("expected local response to start with fake- but was ${result.text}")
        }
    }
}