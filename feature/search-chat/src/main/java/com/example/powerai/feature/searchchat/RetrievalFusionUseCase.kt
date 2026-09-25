package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.domain.retrieval.HybridRetrievalService
import com.example.powerai.domain.usecase.LocalSearchQueryVariant
import com.example.powerai.domain.usecase.LocalSearchQueryVariantBuilder
import com.example.powerai.engine.ai.SparseSearcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Use case wrapper that now uses `HybridRetrievalService` so callers remain unchanged
 * while retrieval uses the new hybrid RRF fusion internally.
 * Multi-variant query plan fusion records strategy and hit counts for diagnostics UI.
 */
class RetrievalFusionUseCase @Inject constructor(
    private val hybridService: HybridRetrievalService,
    private val sparseSearcher: SparseSearcher
) {
    suspend fun invoke(keyword: String, limit: Int = 10, forceAnn: Boolean = false): List<KnowledgeItem> {
        val results = retrieveHybridCandidates(keyword, limit)
        val mapped = results.mapNotNull { rr ->
            val item = rr.item
            if (item != null) {
                val dbg = rr.debug
                if (dbg?.get("fts_bonus_applied") == true) {
                    item.copy(title = "⭐ " + item.title)
                } else {
                    item
                }
            } else null
        }.toMutableList()

        try {
            val sparse = try {
                sparseSearcher.search(keyword, 2)
            } catch (_: Throwable) {
                emptyList<SparseSearcher.SafetyEntry>()
            }
            val sparseMapped = sparse.mapIndexed { idx, s ->
                val synthId = -(System.currentTimeMillis() % 1000000L) - idx
                KnowledgeItem(
                    id = synthId,
                    title = s.title.ifBlank { "(no-title)" },
                    content = s.content,
                    source = s.source.ifBlank { "sparse" },
                    pageNumber = null,
                    category = "sparse",
                    keywords = emptyList(),
                    hitBlockIndex = null,
                    hitBlockId = null
                )
            }
            for (sm in sparseMapped.reversed()) {
                if (mapped.none { it.title == sm.title }) {
                    mapped.add(0, sm)
                }
            }
        } catch (_: Throwable) {}

        return mapped.take(limit)
    }

    suspend fun invokeResults(keyword: String, limit: Int = 10): List<RetrievalResult> {
        val fused = retrieveHybridCandidates(keyword, limit)
        val out = fused.toMutableList()
        try {
            val sparse = try {
                sparseSearcher.search(keyword, 2)
            } catch (_: Throwable) {
                emptyList<SparseSearcher.SafetyEntry>()
            }
            for (s in sparse) {
                val ki = KnowledgeItem(
                    id = -(System.currentTimeMillis() % 1000000L),
                    title = s.title.ifBlank { "(no-title)" },
                    content = s.content,
                    source = s.source.ifBlank { "sparse" },
                    pageNumber = null,
                    category = "sparse",
                    keywords = emptyList(),
                    hitBlockIndex = null,
                    hitBlockId = null
                )
                val rr = RetrievalResult(
                    id = null,
                    score = 1.0f,
                    confidence = 1.0f,
                    source = "sparse",
                    metadata = mapOf("title" to ki.title, "snippet" to ki.content, "source" to ki.source),
                    provenance = emptyList(),
                    vectorPresent = false,
                    debug = mapOf("sparse" to true),
                    item = ki
                )
                out.add(0, rr)
            }
        } catch (_: Throwable) {}
        return out.take(limit)
    }

    private suspend fun retrieveHybridCandidates(keyword: String, limit: Int): List<RetrievalResult> {
        val variants = LocalSearchQueryVariantBuilder.buildPlan(keyword)
        if (variants.isEmpty()) return emptyList()

        val aggregates = linkedMapOf<String, RetrievalAggregate>()
        val plannedResults = coroutineScope {
            variants.mapIndexed { index, variant ->
                async {
                    IndexedVariantResult(
                        index = index,
                        variant = variant,
                        results = hybridService.retrieveHybrid(variant.text, limit)
                    )
                }
            }.awaitAll().sortedBy { it.index }
        }

        for (planned in plannedResults) {
            mergeCandidateResults(
                target = aggregates,
                candidate = planned.variant,
                variantIndex = planned.index,
                results = planned.results
            )
        }

        return aggregates.values
            .map { it.toRetrievalResult() }
            .sortedByDescending { it.confidence ?: it.score }
            .take(limit)
    }

    private fun mergeCandidateResults(
        target: MutableMap<String, RetrievalAggregate>,
        candidate: LocalSearchQueryVariant,
        variantIndex: Int,
        results: List<RetrievalResult>
    ) {
        results.forEach { result ->
            val score = (result.confidence ?: result.score).coerceIn(0f, 1f)
            val decorated = decorateCandidateResult(result, candidate, variantIndex)
            val key = resultKey(decorated)
            val existing = target[key]
            target[key] = if (existing == null) {
                RetrievalAggregate(best = decorated, bestScore = score, hitCount = 1)
            } else {
                val preferred = if (score > existing.bestScore) decorated else mergeDebug(existing.best, decorated)
                RetrievalAggregate(
                    best = preferred,
                    bestScore = maxOf(existing.bestScore, score),
                    hitCount = existing.hitCount + 1
                )
            }
        }
    }

    private fun decorateCandidateResult(
        result: RetrievalResult,
        candidate: LocalSearchQueryVariant,
        variantIndex: Int
    ): RetrievalResult {
        val debug = result.debug?.toMutableMap() ?: mutableMapOf()
        debug["query_variant"] = candidate.text
        debug["query_plan_strategy"] = candidate.strategy
        debug["query_plan_priority"] = candidate.priority
        if (variantIndex > 0) {
            debug["query_rewritten"] = true
        }
        return result.copy(debug = debug)
    }

    private fun mergeDebug(preferred: RetrievalResult, incoming: RetrievalResult): RetrievalResult {
        val mergedDebug = preferred.debug?.toMutableMap() ?: mutableMapOf()
        incoming.debug?.forEach { (key, value) ->
            mergedDebug.putIfAbsent(key, value)
        }
        return preferred.copy(debug = mergedDebug)
    }

    private fun resultKey(result: RetrievalResult): String {
        result.id?.let { return "id:$it" }
        val title = result.item?.title ?: result.metadata["title"].orEmpty()
        val source = result.item?.source ?: result.metadata["source"] ?: result.source
        val content = (result.item?.content ?: result.metadata["snippet"].orEmpty()).take(120)
        return "$title|$source|$content"
    }

    private data class RetrievalAggregate(
        val best: RetrievalResult,
        val bestScore: Float,
        val hitCount: Int
    ) {
        fun toRetrievalResult(): RetrievalResult {
            val mergedScore = (bestScore + 0.03f * (hitCount - 1)).coerceIn(0f, 1f)
            val debug = best.debug?.toMutableMap() ?: mutableMapOf()
            debug["query_variant_hit_count"] = hitCount
            return best.copy(score = mergedScore, confidence = mergedScore, debug = debug)
        }
    }

    private data class IndexedVariantResult(
        val index: Int,
        val variant: LocalSearchQueryVariant,
        val results: List<RetrievalResult>
    )
}
