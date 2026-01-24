package com.example.powerai.domain.ai

object AiPromptProvider {
    private const val basePrompt = "你是电力线路工助手，只能根据提供的本地技规回答问题。未明确规定请回答‘技规未明确规定’。"
    fun getPromptWithContext(contextText: String, question: String): String {
        return "$basePrompt\n\n技规内容：$contextText\n\n问题：$question"
    }
}
