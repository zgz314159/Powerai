package com.example.powerai.domain.usecase

import com.example.powerai.core.model.RetrievalResult

data class LocalEvidenceRefinement(
    val retrievals: List<RetrievalResult>,
    val totalCandidateCount: Int,
    val refinedCandidateCount: Int,
    val discardedCandidateCount: Int,
    val discardedCandidates: List<LocalDiscardedCandidate> = emptyList()
)

enum class LocalDiscardReason(
    val code: String,
    val label: String
) {
    NOISY_MARKERS("noisy_markers", "噪声标记偏强"),
    WEAK_DEFINITION_EVIDENCE("weak_definition_evidence", "定义型证据特征不"),
    LOW_COVERAGE("low_coverage", "覆盖度过"),
    MISSING_ANCHOR("missing_anchor", "缺少关键锚点"),
    LOW_RELEVANCE("low_relevance", "综合相关性不")
}

data class LocalDiscardedCandidate(
    val title: String,
    val source: String,
    val reason: LocalDiscardReason,
    val reasonDetailCode: String = "",
    val reasonDetail: String = "",
    val thresholdSnapshot: LocalDiscardThresholdSnapshot = LocalDiscardThresholdSnapshot(),
    val tokenCoverage: String = "",
    val contextualSignal: String = ""
)

data class LocalDiscardThresholdSnapshot(
    val combinedSignal: String = "",
    val combinedThreshold: String = "",
    val anchorHits: Int = 0,
    val anchorRequired: Boolean = false,
    val primaryFailedGate: String = "",
    val failedGateCount: Int = 0,
    val gates: List<LocalDiscardRuleGateSnapshot> = emptyList()
)

data class LocalDiscardRuleGateSnapshot(
    val code: String,
    val passed: Boolean,
    val actual: String = "",
    val expected: String = ""
)

internal data class LocalDiscardExplanation(
    val reason: LocalDiscardReason,
    val detailCode: String,
    val detail: String
)
