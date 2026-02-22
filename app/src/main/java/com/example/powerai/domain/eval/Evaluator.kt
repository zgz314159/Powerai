package com.example.powerai.domain.eval

import com.example.powerai.domain.model.KnowledgeItem
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
}
