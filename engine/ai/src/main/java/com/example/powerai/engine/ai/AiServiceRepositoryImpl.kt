package com.example.powerai.engine.ai

import com.example.powerai.core.model.chat.ChatCompletionsRequest
import com.example.powerai.core.model.chat.ChatCompletionsResponse
import com.example.powerai.core.model.chat.ChatChoice
import com.example.powerai.core.model.chat.ChatMessage
import com.example.powerai.core.repository.AiServiceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiServiceRepositoryImpl @Inject constructor(
    private val api: AiApiService
) : AiServiceRepository {

    override suspend fun chatCompletions(request: ChatCompletionsRequest): ChatCompletionsResponse {
        val apiRequest = ApiChatCompletionsRequest(
            model = request.model,
            messages = request.messages.map { ApiChatMessage(it.role, it.content) },
            stream = request.stream
        )

        val resp = api.chatCompletions(apiRequest)

        return ChatCompletionsResponse(
            id = resp.id ?: "",
            choices = resp.choices?.map { choice ->
                ChatChoice(
                    index = choice.index ?: 0,
                    message = choice.message?.let { ChatMessage(it.role, it.content) }
                        ?: choice.delta?.let { ChatMessage(it.role, it.content) }
                        ?: ChatMessage("assistant", ""),
                    finishReason = choice.finish_reason
                )
            } ?: emptyList(),
            created = System.currentTimeMillis() / 1000
        )
    }
}
