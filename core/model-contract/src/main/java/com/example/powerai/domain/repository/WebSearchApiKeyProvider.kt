package com.example.powerai.domain.repository

/**
 * Provides the Serper (Google Custom Search via Serper) API key without coupling
 * data layer code to app [BuildConfig].
 */
fun interface WebSearchApiKeyProvider {
    fun serperApiKey(): String
}
