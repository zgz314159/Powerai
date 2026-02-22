package com.example.powerai.data.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class VisionBoostJsonTest {

    @Test
    fun extractChatContent_openAiMessageContent() {
        val raw = """
            {
              "choices": [
                {"message": {"role": "assistant", "content": "|a|b|\n|---|---|\n|1|2|"}}
              ]
            }
        """.trimIndent()

        assertEquals("|a|b|\n|---|---|\n|1|2|", VisionBoostJson.extractChatContent(raw))
    }

    @Test
    fun extractChatContent_stripsCodeFences() {
        val raw = """
            {
              "choices": [
                {"message": {"content": "```markdown\n|a|b|\n|---|---|\n|1|2|\n```"}}
              ]
            }
        """.trimIndent()

        assertEquals("|a|b|\n|---|---|\n|1|2|", VisionBoostJson.extractChatContent(raw))
    }
}
