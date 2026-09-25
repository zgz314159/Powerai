package com.example.powerai.domain.llm

import org.junit.Assert.*
import org.junit.Test
import com.example.powerai.engine.ai.GemmaReflectionHelper

// Public top-level so reflective invoke from GemmaReflectionHelper can access methods.
class GemmaReflectionDummyEngine {
    fun generate(prompt: String, opts: Map<String, Any>): String {
        return "generated-$prompt"
    }
}

class GemmaReflectionAsyncEngine {
    fun generateResponseAsync(prompt: String, listener: java.util.function.BiConsumer<String, Boolean>) {
        listener.accept("async-$prompt", true)
    }
}

class GemmaReflectionHelperTest {

    @Test
    fun invokeGenerate_prefersSimpleGenerate() {
        val engine = GemmaReflectionDummyEngine()
        val result = GemmaReflectionHelper.invokeGenerate(engine, "hello", emptyMap())
        assertEquals("generated-hello", result.text)
    }

    @Test
    fun invokeGenerate_handlesAsyncSignature() {
        val engine = GemmaReflectionAsyncEngine()
        val result = GemmaReflectionHelper.invokeGenerate(engine, "world", emptyMap())
        assertEquals("async-world", result.text)
    }

    @Test
    fun applySampling_invokesMethodsIfPresent() {
        class Builder {
            var temperature: Float? = null
            var topK: Int? = null
            var rep: Float? = null
            fun setTemperature(t: Float) { temperature = t }
            fun setTopK(k: Int) { topK = k }
            fun setRepetitionPenalty(r: Float) { rep = r }
        }
        val b = Builder()
        GemmaReflectionHelper.applySampling(b)
        assertEquals(0.75f, b.temperature)
        assertEquals(40, b.topK)
        assertEquals(1.2f, b.rep)
    }
}