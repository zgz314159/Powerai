package com.example.powerai.data.repository

import com.example.powerai.data.remote.search.GoogleCustomSearchClient
import com.example.powerai.domain.repository.WebSearchRepository
import com.example.powerai.domain.repository.WebSearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSearchRepositoryImpl @Inject constructor(
    private val client: GoogleCustomSearchClient
) : WebSearchRepository {

    override fun isConfigured(): Boolean = client.isConfigured()

    override suspend fun search(query: String, count: Int): List<WebSearchResult> {
        return client.search(query, count).map {
            WebSearchResult(it.title, it.url, it.snippet)
        }
    }
}
