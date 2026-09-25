package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.domain.common.JsonUtils

/**
 * Helper for constructing the JSON fragments passed to the AI streaming API.
 * Extracted from [AiStreamUseCase] to reduce the size of that class and enable
 * focused unit tests.
 */
object AiStreamRequestBuilder {
    /**
     * Build a list of JSON-encoded message objects representing the recent
     * conversation history followed by the current user query.  The fragments
     * are suitable for inclusion inside the `"messages"` array of the
     * OpenAI/DeepSeek request body.
     */
    fun buildMessageFragments(historyTurns: List<ChatTurn>, userInput: String): List<String> {
        return buildList {
            historyTurns.forEach { turn ->
                if (turn.question.isNotBlank()) {
                    add("{\"role\":\"user\",\"content\":\"${JsonUtils.escapeJson(turn.question)}\"}")
                }
                if (turn.answer.isNotBlank()) {
                    add("{\"role\":\"assistant\",\"content\":\"${JsonUtils.escapeJson(turn.answer)}\"}")
                }
            }
            add("{\"role\":\"user\",\"content\":\"${JsonUtils.escapeJson(userInput)}\"}")
        }
    }
}
