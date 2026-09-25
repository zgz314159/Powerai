package com.example.powerai.core.model.chat

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatCompletionsRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024
)

data class ChatCompletionsResponse(
    val id: String,
    val choices: List<ChatChoice>,
    val created: Long
)

data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
    val finishReason: String?
)