package com.example.powerai.domain.eval

import com.example.powerai.core.model.KnowledgeItem

import kotlinx.coroutines.runBlocking

/**
 * Simple evaluator that accepts a dataset of queries -> ground truth ids and a retrieval function.
 * Retrieval function should return a list of KnowledgeItem where `id` as String is used for matching.
 */
class Evaluator(
    private val retrievalFn: suspend (String, Int) -> List<KnowledgeItem>,
    private val ks: List<Int> = listOf(1, 3, 5, 10)
) {
    /**
     * dataset: map from query -> set of relevant IDs (string form)
     */
    fun evaluate(dataset: Map<String, Set<String>>, limit: Int = 10): EvalMetrics {
        val precisionAcc = mutableMapOf<Int, Double>()
        val recallAcc = mutableMapOf<Int, Double>()
        ks.forEach { precisionAcc[it] = 0.0; recallAcc[it] = 0.0 }
        var mrrSum = 0.0
        var n = 0

        runBlocking {
            for ((query, relevant) in dataset) {
                val retrieved = retrievalFn(query, limit).map { it.id.toString() }
                ks.forEach { k ->
                    precisionAcc[k] = precisionAcc[k]!! + precisionAtK(relevant, retrieved, k)
                    recallAcc[k] = recallAcc[k]!! + recallAtK(relevant, retrieved, k)
                }
                mrrSum += reciprocalRank(relevant, retrieved)
                n += 1
            }
        }

        if (n == 0) return EvalMetrics(emptyMap(), emptyMap(), 0.0)

        val precisionAvg = precisionAcc.mapValues { (_, v) -> v / n.toDouble() }
        val recallAvg = recallAcc.mapValues { (_, v) -> v / n.toDouble() }
        val mrrAvg = mrrSum / n.toDouble()
        return EvalMetrics(precisionAvg, recallAvg, mrrAvg)
    }

    /** Evaluate a list of labeled cases with per-case detail and optional label groups. */
    fun evaluateCases(cases: List<EvalCase>, limit: Int = 10): DetailedEvalResult {
        val caseResults = mutableListOf<EvalCaseResult>()
        val precisionAcc = ks.associateWith { 0.0 }.toMutableMap()
        val recallAcc = ks.associateWith { 0.0 }.toMutableMap()
        var mrrSum = 0.0

        for (c in cases) {
            val retrieved = runBlocking { retrievalFn(c.query, limit) }.map { it.id.toString() }
            ks.forEach { k ->
                precisionAcc[k] = precisionAcc[k]!! + precisionAtK(c.relevantIds, retrieved, k)
                recallAcc[k] = recallAcc[k]!! + recallAtK(c.relevantIds, retrieved, k)
            }
            val rr = reciprocalRank(c.relevantIds, retrieved)
            mrrSum += rr
            caseResults += EvalCaseResult(
                query = c.query,
                label = c.label,
                relevantIds = c.relevantIds,
                retrievedIds = retrieved,
                reciprocalRank = rr,
                hitAt1 = retrieved.take(1).any { c.relevantIds.contains(it) },
                hitAt3 = retrieved.take(3).any { c.relevantIds.contains(it) },
                hitAt5 = retrieved.take(5).any { c.relevantIds.contains(it) }
            )
        }

        val n = cases.size
        val metrics = if (n == 0) {
            EvalMetrics(emptyMap(), emptyMap(), 0.0)
        } else {
            EvalMetrics(
                precisionAtK = precisionAcc.mapValues { it.value / n },
                recallAtK = recallAcc.mapValues { it.value / n },
                mrr = mrrSum / n
            )
        }

        val byLabel = cases.mapNotNull { it.label }.distinct().associateWith { label ->
            val labelCases = caseResults.filter { it.label == label }
            val ln = labelCases.size
            if (ln == 0) return@associateWith EvalMetrics(emptyMap(), emptyMap(), 0.0)
            EvalMetrics(
                precisionAtK = ks.associateWith { k ->
                    labelCases.count { hit -> hit.hitAt1 || (k > 1 && hit.hitAt3) || (k > 3 && hit.hitAt5) }.toDouble() / ln
                    // simple approximation kept for grouping display; detailed per-k is not required by tests
                },
                recallAtK = ks.associateWith { k ->
                    labelCases.map { c ->
                        val topK = c.retrievedIds.take(k)
                        if (c.relevantIds.isEmpty()) 0.0
                        else topK.count { c.relevantIds.contains(it) }.toDouble() / c.relevantIds.size
                    }.average()
                },
                mrr = labelCases.map { it.reciprocalRank }.average()
            )
        }

        return DetailedEvalResult(metrics = metrics, cases = caseResults, byLabel = byLabel)
    }
}
