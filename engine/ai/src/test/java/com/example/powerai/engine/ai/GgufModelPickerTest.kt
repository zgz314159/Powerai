package com.example.powerai.engine.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GgufModelPickerTest {
    @Test
    fun `prefers instruct q5 over distill q4`() {
        val distill = File("DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf")
        val instruct = File("Qwen2.5-3B-Instruct-Q5_K_M.gguf")
        val picked = GgufModelPicker.pickPreferred(arrayOf(distill, instruct))
        assertEquals(instruct.name, picked!!.name)
    }

    @Test
    fun `q6 scores above q3`() {
        assertTrue(GgufModelPicker.score("model-Q6_K.gguf") > GgufModelPicker.score("model-Q3_K_M.gguf"))
    }
}
