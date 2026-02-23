package com.example.powerai.domain.retrieval

import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.model.RetrievalResult
import com.example.powerai.domain.repository.KnowledgeRepository
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.domain.retriever.AnnRetriever
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight retrieval fusion service which composes results from the domain `KnowledgeRepository`
 * and applies simple scoring/re-ranking heuristics. Supports a `forceAnn` path that bypasses
 * lexical-first logic and directly queries the ANN retriever, mapping returned ids back into
 * `KnowledgeItem`s from Room.
 */
@Singleton
class RetrievalFusionService @Inject constructor(
    private val repository: KnowledgeRepository,
    private val observability: com.example.powerai.util.ObservabilityService,
    private val annRetriever: AnnRetriever,
    private val knowledgeDao: KnowledgeDao
) {
    suspend fun retrieve(query: String, limit: Int = 10, forceAnn: Boolean = false): List<RetrievalResult> {
        val start = System.currentTimeMillis()
        observability.retrievalStarted(query)

        val raw: List<KnowledgeItem> = if (forceAnn) {
            // Force ANN path: call annRetriever directly and map ids -> KnowledgeItem via Room.
            try {
                val ids = annRetriever.search(query, limit)
                // ann ids emitted previously for debugging — removed in production
                val items = mutableListOf<KnowledgeItem>()
                for (id in ids) {
                    try {
                        val entity = knowledgeDao.getById(id)
                        // per-id debug logging removed in production build
                        if (entity != null && entity.content.isNotBlank()) {
                            items.add(
                                KnowledgeItem(
                                    id = entity.id,
                                    title = "[AI][Semantic] " + entity.title.ifBlank { "向量检索结果" },
                                    content = entity.content,
                                    source = entity.source,
                                    pageNumber = entity.pageNumber,
                                    category = entity.category.ifBlank { "VECTOR" },
                                    keywords = if (entity.keywordsSerialized.isBlank()) emptyList() else entity.keywordsSerialized.split(',')
                                )
                            )
                        }
                    } catch (_: Throwable) {
                    }
                }
                items
            } catch (_: Throwable) {
                emptyList()
            }
        } else {
            try {
                repository.searchLocal(query)
            } catch (_: Throwable) {
                emptyList()
            }
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
