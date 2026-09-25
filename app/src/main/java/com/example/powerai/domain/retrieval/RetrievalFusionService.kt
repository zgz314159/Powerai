package com.example.powerai.domain.retrieval

import com.example.powerai.core.repository.KnowledgeRepository

import com.example.powerai.core.model.KnowledgeItem

import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.core.repository.AnnRetriever
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
    private val observability: com.example.powerai.core.model.ObservabilityService,
    private val annRetriever: AnnRetriever
) {
    suspend fun retrieve(query: String, limit: Int = 10, forceAnn: Boolean = false): List<RetrievalResult> {
        val start = System.currentTimeMillis()
        observability.retrievalStarted(query)

        val raw: List<KnowledgeItem> = if (forceAnn) {
            // Force ANN path: call annRetriever directly and map ids -> KnowledgeItem via Repository.
            try {
                val hits = annRetriever.search(query, limit)
                // Emit ANN ids for mapping trace (feature branch debugging)
                try {
                    val idsStr = hits.mapNotNull { it.id }.joinToString(",")
                    observability.logEvent("ANN", "ann_ids=$idsStr")
                } catch (_: Throwable) {}
                val items = mutableListOf<KnowledgeItem>()
                for (hit in hits) {
                    val id = hit.id ?: continue
                    try {
                        val item = repository.getLocalItemById(id)
                        try {
                            if (item == null) {
                                observability.logEvent("ANN", "id=$id mapped=null")
                            } else {
                                observability.logEvent("ANN", "id=$id mapped=found title=${item.title.take(60)}")
                            }
                        } catch (_: Throwable) {}
                        if (item != null && item.content.isNotBlank()) {
                            items.add(
                                item.copy(
                                    title = "[AI][Semantic] " + item.title.ifBlank { "向量检索结" },
                                    category = item.category.ifBlank { "VECTOR" }
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
