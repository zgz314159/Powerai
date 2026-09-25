package com.example.powerai.domain.eval

data class EvalCase(
    val query: String,
    val relevantIds: Set<String>,
    val label: String? = null
)

data class EvalCaseResult(
    val query: String,
    val label: String? = null,
    val relevantIds: Set<String>,
    val retrievedIds: List<String>,
    val reciprocalRank: Double,
    val hitAt1: Boolean,
    val hitAt3: Boolean,
    val hitAt5: Boolean
)

data class DetailedEvalResult(
    val metrics: EvalMetrics,
    val cases: List<EvalCaseResult>,
    val byLabel: Map<String, EvalMetrics> = emptyMap()
)
