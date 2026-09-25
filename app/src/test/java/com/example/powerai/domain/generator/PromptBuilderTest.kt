package com.example.powerai.domain.generator

import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.*
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `prompt builder truncates by token budget and preserves star markers`() {
        // Construct content: make the priority (3rd) short so it will fit, others long to force truncation
        val longText = List(1200) { "这是一个测试句子" }.joinToString(" ")
        val shortText = "这是关于变压器的关键说明。"

        // 5 evidence items; mark the 3rd as precise match with short content
        val results = (1..5).map { i ->
            val content = if (i == 3) shortText else longText
            RetrievalResult(
                id = i.toLong(),
                score = 0.5f,
                confidence = 0.5f + i * 0.05f,
                source = "doc",
                metadata = mapOf("source" to "manual.pdf", "title" to "标题$i"),
                item = KnowledgeItem(id = i.toLong(), title = "标题$i", content = content, source = "manual.pdf", category = "DOC", keywords = emptyList()),
                debug = if (i == 3) mapOf("fts_bonus_applied" to true) else null
            )
        }

        // Use smaller budget to force truncation
        val builder = PromptBuilder(tokenBudget = 800, safetyMargin = 100)
        val prompt = builder.buildPrompt("变压器", results)

        // Print full prompt for audit
        println("--- fullPromptText START ---")
        println(prompt.user)
        println("--- fullPromptText END ---")

        // Verify that the first evidence block contains the [精准匹配] marker
        val evidenceSection = prompt.user.substringAfter("证据:\n")
        val firstBlock = evidenceSection.split("\n\n").firstOrNull() ?: ""
        assertTrue("first block should contain [精准匹配]", firstBlock.contains("[精准匹配]"))

        // Verify truncation: the total token-like count should be <= tokenBudget + safetyMargin
        val tokenCount = prompt.user.trim().length // length used as proxy for token-ish measure
        assertTrue("prompt length=$tokenCount should be <= budget+margin", tokenCount <= (800 + 100) * 4)
    }
}
