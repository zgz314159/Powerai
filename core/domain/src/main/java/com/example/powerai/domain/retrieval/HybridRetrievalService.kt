package com.example.powerai.domain.retrieval

import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.core.repository.AnnRetriever
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Named

/**
 * Hybrid retrieval service that merges vector (ANN) results and keyword (FTS) results
 * using Reciprocal Rank Fusion (RRF).
 */
class HybridRetrievalService @Inject constructor(
    private val annRetriever: AnnRetriever,
    private val ftsRetriever: FtsRetriever,
    private val rrfK: Int = 60,
    /** source weights are injected via DI (Named("retrieval_source_weights")) */
    @Named("retrieval_source_weights") private val sourceWeights: Map<String, Double> = mapOf(),
    /** ftsTopBonus injected via DI (Named("fts_top_bonus")). Default 0.0 when instantiated directly. */
    @Named("fts_top_bonus") private val ftsTopBonus: Double = 0.0
) {
    suspend fun retrieveHybrid(query: String, topK: Int = 10): List<RetrievalResult> = coroutineScope {
        // logging removed: retrieveHybrid invoked
        val annDeferred = async { annRetriever.search(query, topK) }
        val ftsDeferred = async { ftsRetriever.search(query, topK) }

        val annResults = annDeferred.await()
        val ftsResults = ftsDeferred.await()

        combineResults(annResults, ftsResults, topK, query)
    }

    /**
     * Detect if the FTS hit should receive the top bonus based on hard match.
     * Returns bonus value (ftsTopBonus) when matched, else 0.0.
     */
    private fun calculateFtsBonus(query: String, ftsResult: RetrievalResult): Double {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0.0

        // Prefer explicit title on the item, then metadata title
        val title = ftsResult.item?.title?.trim()?.lowercase()
            ?: ftsResult.metadata["title"]?.trim()?.lowercase()
            ?: ""

        if (title.isEmpty()) return 0.0

        // Exact match or fully contained -> hard match
        if (title == q) {
            // exact-match bonus audit log removed
            return ftsTopBonus
        }
        if (title.contains(q) || q.contains(title)) {
            // containment-match bonus audit log removed
            return ftsTopBonus
        }

        return 0.0
    }

    /**
     * Combine two ranked lists using RRF. Items are keyed by their `id` (must be non-null).
     * Returns results ordered by fused score desc, with normalized `score` and `confidence` (0..1).
     */
    fun combineResults(vectorResults: List<RetrievalResult>, ftsResults: List<RetrievalResult>, topK: Int = 10, query: String = ""): List<RetrievalResult> {
        val scores = mutableMapOf<Long, Double>()
        val repr = mutableMapOf<Long, RetrievalResult>()

        fun addList(list: List<RetrievalResult>) {
            list.forEachIndexed { idx, r ->
                val id = r.id ?: return@forEachIndexed
                val rank = idx + 1
                val weight = sourceWeights[r.source] ?: 1.0
                val add = weight * (1.0 / (rrfK + rank))
                scores[id] = (scores[id] ?: 0.0) + add
                repr.putIfAbsent(id, r)
            }
        }

        addList(vectorResults)
        addList(ftsResults)

        // Dynamic hard-match detector: apply ftsTopBonus only when query fully contained in title/metadata
        if (ftsTopBonus > 0.0 && query.isNotBlank()) {
            ftsResults.forEachIndexed { idx, r ->
                val id = r.id ?: return@forEachIndexed
                val bonus = calculateFtsBonus(query, r)
                if (bonus > 0.0) {
                    scores[id] = (scores[id] ?: 0.0) + bonus
                    // update debug map on repr if present
                    val existing = repr[id]
                    if (existing != null) {
                        val dbg = existing.debug?.toMutableMap() ?: mutableMapOf()
                        dbg["fts_bonus_applied"] = true
                        repr[id] = existing.copy(debug = dbg)
                        val title = existing.item?.title ?: existing.metadata["title"] ?: "(no title)"
                        // applied bonus logging removed
                    }
                }
            }
        }

        if (scores.isEmpty()) return emptyList()

        val maxScore = scores.values.maxOrNull() ?: 1.0

        val merged = scores.map { (id, fused) ->
            val rep = repr[id]
            val norm = (fused / maxScore).toFloat()
            if (rep != null) {
                rep.copy(score = norm, confidence = norm)
            } else {
                RetrievalResult(id = id, score = norm, confidence = norm, source = "hybrid")
            }
        }

        val out = merged.sortedByDescending { it.score }.take(topK)
        // combineResults final logging removed
        return out
    }
}

/** Simple FTS retriever abstraction so Hybrid service can be tested without Room dependency. */
interface FtsRetriever {
    suspend fun search(query: String, k: Int = 10): List<RetrievalResult>
}
