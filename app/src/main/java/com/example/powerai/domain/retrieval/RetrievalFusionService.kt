package com.example.powerai.domain.retrieval

import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.model.RetrievalResult
import com.example.powerai.domain.repository.KnowledgeRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight retrieval fusion service which composes results from the domain `KnowledgeRepository`
 * and applies simple scoring/re-ranking heuristics. Designed to be a single place to extend
 * more advanced fusion (vectors, lexical, re-ranking) later.
 */
@Singleton
class RetrievalFusionService @Inject constructor(
    private val repository: KnowledgeRepository,
    private val observability: com.example.powerai.util.ObservabilityService
) {
    suspend fun retrieve(query: String, limit: Int = 10): List<RetrievalResult> {
        val start = System.currentTimeMillis()
        observability.retrievalStarted(query)

        val raw = try {
            repository.searchLocal(query)
        } catch (_: Throwable) {
            emptyList<KnowledgeItem>()
        }

        if (raw.isEmpty()) {
            observability.retrievalFinished(query, 0, System.currentTimeMillis() - start)
            return emptyList()
        }

        val q = query.trim().lowercase()

        val scored = raw.map { item ->
            var score = when (item.category.uppercase()) {
                "VECTOR" -> 0.6f
                else -> 0.85f
            }

            if (item.title.lowercase().contains(q)) score += 0.08f
            if (item.content.lowercase().contains(q)) score += 0.05f
            if (!item.keywords.isNullOrEmpty() && item.keywords.any { it.lowercase().contains(q) }) score += 0.04f

            if (item.content.length < 60) score -= 0.06f

            RetrievalResult(item = item, score = score.coerceIn(0f, 1f))
        }

        val result = scored.sortedByDescending { it.score }.take(limit)
        observability.retrievalFinished(query, result.size, System.currentTimeMillis() - start)
        return result
    }
}
