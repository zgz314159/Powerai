package com.example.powerai.domain.eval

/** Basic retrieval metrics container */
data class EvalMetrics(
    val precisionAtK: Map<Int, Double>,
    val recallAtK: Map<Int, Double>,
    val mrr: Double
)

/** Compute precision@k for a single query */
fun precisionAtK(relevant: Set<String>, retrieved: List<String>, k: Int): Double {
    if (k <= 0) return 0.0
    val topK = retrieved.take(k)
    if (topK.isEmpty()) return 0.0
    val hit = topK.count { relevant.contains(it) }
    return hit.toDouble() / k.toDouble()
}

/** Compute recall@k for a single query */
fun recallAtK(relevant: Set<String>, retrieved: List<String>, k: Int): Double {
    if (relevant.isEmpty()) return 0.0
    val topK = retrieved.take(k)
    val hit = topK.count { relevant.contains(it) }
    return hit.toDouble() / relevant.size.toDouble()
}

/** Compute reciprocal rank for a single query */
fun reciprocalRank(relevant: Set<String>, retrieved: List<String>): Double {
    for ((i, id) in retrieved.withIndex()) {
        if (relevant.contains(id)) return 1.0 / (i + 1).toDouble()
    }
    return 0.0
}
