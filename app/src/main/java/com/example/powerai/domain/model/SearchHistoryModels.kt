package com.example.powerai.domain.model

/**
 * Represent a single search history entry.
 */
data class SearchEntry(
    val query: String,
    val timestamp: Long
)

// For backward compatibility while refactoring
typealias LocalSearchEntry = SearchEntry
