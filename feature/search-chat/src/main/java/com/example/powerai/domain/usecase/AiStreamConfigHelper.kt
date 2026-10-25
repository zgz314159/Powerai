package com.example.powerai.domain.usecase

import com.example.powerai.core.repository.RemoteConfigRepository
import com.example.powerai.domain.repository.WebSearchRepository

/**
 * Configuration and validation helpers for AI streaming requests.
 *
 * Extracted from [AiStreamUseCase.askAiStream] to reduce method complexity.
 */
internal object AiStreamConfigHelper {

    /**
     * Validates AI API configuration and returns the endpoint URL and model name.
     *
     * @return Pair of (url, model) if valid
     * @throws IllegalStateException if configuration is invalid
     */
    fun validateApiConfig(config: RemoteConfigRepository): Pair<String, String> {
        val baseUrl = config.getAiBaseUrl().trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            throw IllegalStateException(
                "AI 未配置：请在本地通过 Gradle 配置 AI_BASE_URL（或在 local.properties 中设置）"
            )
        }
        val url = "$baseUrl/chat/completions"
        val model = config.getDeepSeekModel().trim().ifBlank { "deepseek-chat" }
        return Pair(url, model)
    }

    /**
     * Performs web search using Google Custom Search if enabled and configured.
     *
     * @param webSearchRepository The web search repository
     * @param webSearchEnabled Whether web search is enabled
     * @param query The search query
     * @return List of source URLs (max 5, deduplicated)
     */
    suspend fun performWebSearch(
        webSearchRepository: WebSearchRepository,
        webSearchEnabled: Boolean,
        query: String
    ): List<String> {
        val cseConfigured = try {
            webSearchRepository.isConfigured()
        } catch (_: Throwable) {
            false
        }

        val results = if (webSearchEnabled && cseConfigured) {
            try {
                webSearchRepository.search(query, count = 5)
            } catch (_: Throwable) {
                emptyList()
            }
        } else {
            emptyList()
        }

        return results.map { it.url }.filter { it.isNotBlank() }.distinct().take(5)
    }
}
