package com.example.powerai.ui.screen.hybrid

enum class LocalQueryIntent {
    FACT_QUESTION,
    TOPIC_OVERVIEW,
    PROCEDURE,
    COMPARISON
}

enum class LocalPageState {
    IDLE,
    SEARCHING,
    EVIDENCE_ONLY,
    ANSWER_READY,
    TOPIC_OVERVIEW,
    INSUFFICIENT_EVIDENCE,
    ERROR
}

data class LocalDiagnosticSnapshot(
    val queryPlanPreview: String = "",
    val queryPlanStrategy: String = "",
    val queryVariant: String = "",
    val queryVariantHitCount: String = "",
    val topEvidenceTitle: String = "",
    val topEvidenceSource: String = "",
    val topEvidencePreview: String = "",
    val topHits: List<LocalDiagnosticHit> = emptyList(),
    val discardedHits: List<LocalDiscardedDiagnosticHit> = emptyList()
) {
    val hasVisibleContent: Boolean
        get() = queryPlanPreview.isNotBlank() || queryPlanStrategy.isNotBlank() || queryVariant.isNotBlank() || topEvidenceTitle.isNotBlank() || topHits.isNotEmpty() || discardedHits.isNotEmpty()
}

data class LocalDiagnosticHit(
    val rank: Int,
    val title: String,
    val source: String,
    val score: String = "",
    val rerankScore: String = "",
    val contextualSignal: String = "",
    val anchorHits: String = "",
    val qaStyle: String = ""
)

data class LocalDiscardedDiagnosticHit(
    val title: String,
    val source: String,
    val reasonCode: String,
    val reason: String,
    val reasonDetailCode: String = "",
    val reasonDetail: String = "",
    val combinedSignal: String = "",
    val combinedThreshold: String = "",
    val anchorHits: String = "",
    val anchorRequired: String = "",
    val primaryFailedGate: String = "",
    val failedGateCount: String = "",
    val gateSummaries: List<LocalDiagnosticGateSummary> = emptyList(),
    val tokenCoverage: String = "",
    val contextualSignal: String = ""
)

data class LocalDiagnosticGateSummary(
    val code: String,
    val passed: String,
    val actual: String = "",
    val expected: String = ""
)

data class LocalSummaryState(
    val pageState: LocalPageState = LocalPageState.IDLE,
    val query: String = "",
    val intent: LocalQueryIntent = LocalQueryIntent.FACT_QUESTION,
    val title: String = "",
    val supportNote: String = "",
    val diagnosticSnapshot: LocalDiagnosticSnapshot = LocalDiagnosticSnapshot(),
    val diagnostics: String = "",
    val summary: String = "",
    val evidenceCount: Int = 0,
    val totalEvidenceCount: Int = 0,
    val isStreaming: Boolean = false,
    val canRunGemma: Boolean = false,
    val errorMessage: String? = null
) {
    val hasVisibleContent: Boolean
        get() = pageState != LocalPageState.IDLE
}
