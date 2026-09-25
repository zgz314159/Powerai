package com.example.powerai.domain.query

enum class QueryIntent {
    FACT_QUESTION,
    TOPIC_OVERVIEW,
    PROCEDURE,
    COMPARISON
}

data class QueryUnderstandingResult(
    val originalQuery: String,
    val normalizedQuery: String,
    val intent: QueryIntent,
    val retrievalQueries: List<String>,
    val signals: Set<String> = emptySet()
)
