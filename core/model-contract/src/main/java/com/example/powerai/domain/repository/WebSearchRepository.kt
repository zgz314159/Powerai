package com.example.powerai.domain.repository

/**
 * Domain model for web search results.
 */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * Interface for web search services.
 */
interface WebSearchRepository {
    /** Returns true if the service is configured with necessary API keys. */
    fun isConfigured(): Boolean

    /** Performs a web search. */
    suspend fun search(query: String, count: Int = 5): List<WebSearchResult>
}
