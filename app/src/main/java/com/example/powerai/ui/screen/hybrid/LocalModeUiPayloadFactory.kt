package com.example.powerai.ui.screen.hybrid

import com.example.powerai.core.model.KnowledgeItem

import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.domain.usecase.HybridQueryUseCase
import com.example.powerai.domain.usecase.LocalDiscardedCandidate
import com.example.powerai.domain.usecase.LocalExtractiveAnswer
import com.example.powerai.domain.usecase.LocalSearchQueryVariantBuilder
import com.example.powerai.util.PdfSourceRef

internal data class LocalModeUiPayload(
    val references: List<KnowledgeItem>,
    val evidence: List<RetrievalResult>,
    val summaryState: LocalSummaryState,
    val gemmaJob: kotlinx.coroutines.Job?
)

internal object LocalModeUiPayloadFactory {
    fun fromOutcome(query: String, outcome: HybridQueryUseCase.LocalModeResult): LocalModeUiPayload {
        val extractiveAnswer = outcome.query.extractiveAnswer
        val retrievals = outcome.query.retrievals.map { retrieval ->
            val item = retrieval.item
            val matched = when {
                extractiveAnswer == null || item == null -> false
                extractiveAnswer.retrievalId != null -> item.id == extractiveAnswer.retrievalId
                outcome.query.retrievals.size == 1 -> true
                else -> false
            }
            val contextLabel = sanitizeContextLabel(
                extractiveLabel = extractiveAnswer?.sourceLabel?.takeIf { matched },
                debugLabel = retrieval.debug?.get("context_label")?.toString(),
                fallbackSource = item?.source ?: retrieval.source
            )
            if (!matched || item == null) {
                if (item == null || contextLabel == null) {
                    retrieval
                } else {
                    retrieval.copy(item = item.copy(contextLabel = contextLabel))
                }
            } else {
                retrieval.copy(
                    item = item.copy(
                        highlightHint = extractiveAnswer?.text,
                        contextLabel = contextLabel ?: item.contextLabel
                    )
                )
            }
        }.sortedWith(
            compareByDescending<RetrievalResult> { retrieval ->
                isAnswerAlignedEvidence(retrieval, extractiveAnswer)
            }.thenByDescending { retrieval ->
                retrieval.confidence ?: retrieval.score
            }
        )

        val items = retrievals.mapNotNull { it.item }
        val diagnosticSnapshot = buildDiagnosticSnapshot(
            query = query,
            retrievals = retrievals,
            extractiveAnswer = extractiveAnswer,
            discardedCandidates = outcome.query.discardedCandidates
        )
        return LocalModeUiPayload(
            references = items,
            evidence = retrievals,
            summaryState = LocalSummaryStateFactory.fromAssessment(
                query = query,
                assessment = outcome.assessment,
                retrievalCount = retrievals.size,
                totalEvidenceCount = outcome.query.totalCandidateCount,
                gemmaRunning = outcome.gemmaJob != null,
                diagnosticSnapshot = diagnosticSnapshot,
                diagnostics = LocalDiagnosticsFormatter.build(
                    query = query,
                    assessment = outcome.assessment,
                    retrievals = retrievals,
                    totalCandidateCount = outcome.query.totalCandidateCount,
                    refinedCandidateCount = outcome.query.refinedCandidateCount,
                    discardedCandidates = outcome.query.discardedCandidates
                ),
                extractiveAnswer = outcome.query.extractiveAnswer
            ),
            gemmaJob = outcome.gemmaJob
        )
    }

    private fun buildDiagnosticSnapshot(
        query: String,
        retrievals: List<RetrievalResult>,
        extractiveAnswer: LocalExtractiveAnswer?,
        discardedCandidates: List<LocalDiscardedCandidate>
    ): LocalDiagnosticSnapshot {
        val top = retrievals.firstOrNull()
        val item = top?.item
        val planPreview = LocalSearchQueryVariantBuilder.buildPlan(query)
            .take(4)
            .joinToString(" || ") { "${it.strategy}:${it.text}" }
        val strategy = top?.debug?.get("query_plan_strategy")?.toString().orEmpty()
        val variant = top?.debug?.get("query_variant")?.toString().orEmpty()
        val hitCount = top?.debug?.get("query_variant_hit_count")?.toString().orEmpty()
        val title = item?.title ?: top?.metadata?.get("title").orEmpty()
        val source = item?.contextLabel
            ?: top?.debug?.get("context_label")?.toString()
            ?: extractiveAnswer?.sourceLabel
            ?: item?.source
            ?: top?.source
            ?: ""
        val preview = extractiveAnswer?.text
            ?.takeIf { text -> text.isNotBlank() }
            ?: item?.highlightHint?.takeIf { hint -> hint.isNotBlank() }
            ?: item?.content.orEmpty().trim().take(48).trim()
        val topHits = retrievals.take(3).mapIndexed { index, result ->
            LocalDiagnosticHit(
                rank = index + 1,
                title = result.item?.title ?: result.metadata["title"].orEmpty(),
                source = sanitizeContextLabel(
                    extractiveLabel = result.item?.contextLabel,
                    debugLabel = result.debug?.get("context_label")?.toString(),
                    fallbackSource = result.item?.source ?: result.source
                ).orEmpty(),
                score = formatScore(result.confidence ?: result.score),
                rerankScore = result.debug?.get("rerank_score")?.toString().orEmpty(),
                contextualSignal = result.debug?.get("contextual_signal")?.toString().orEmpty(),
                anchorHits = result.debug?.get("anchor_hits")?.toString().orEmpty(),
                qaStyle = result.debug?.get("qa_style")?.toString().orEmpty()
            )
        }
        val discardedHits = discardedCandidates.take(3).map { candidate ->
            LocalDiscardedDiagnosticHit(
                title = candidate.title,
                source = candidate.source,
                reasonCode = candidate.reason.code,
                reason = candidate.reason.label,
                reasonDetailCode = candidate.reasonDetailCode,
                reasonDetail = candidate.reasonDetail,
                combinedSignal = candidate.thresholdSnapshot.combinedSignal,
                combinedThreshold = candidate.thresholdSnapshot.combinedThreshold,
                anchorHits = candidate.thresholdSnapshot.anchorHits.toString(),
                anchorRequired = candidate.thresholdSnapshot.anchorRequired.toString(),
                primaryFailedGate = candidate.thresholdSnapshot.primaryFailedGate,
                failedGateCount = candidate.thresholdSnapshot.failedGateCount.toString(),
                gateSummaries = candidate.thresholdSnapshot.gates.map { gate ->
                    LocalDiagnosticGateSummary(
                        code = gate.code,
                        passed = gate.passed.toString(),
                        actual = gate.actual,
                        expected = gate.expected
                    )
                },
                tokenCoverage = candidate.tokenCoverage,
                contextualSignal = candidate.contextualSignal
            )
        }

        return LocalDiagnosticSnapshot(
            queryPlanPreview = planPreview,
            queryPlanStrategy = strategy,
            queryVariant = variant,
            queryVariantHitCount = hitCount,
            topEvidenceTitle = title,
            topEvidenceSource = source,
            topEvidencePreview = preview,
            topHits = topHits,
            discardedHits = discardedHits
        )
    }

    private fun formatScore(value: Float): String {
        return String.format(java.util.Locale.US, "%.2f", value)
    }

    private fun sanitizeContextLabel(
        extractiveLabel: String?,
        debugLabel: String?,
        fallbackSource: String?
    ): String? {
        val label = PdfSourceRef.cleanCompositeLabel(extractiveLabel)
            .ifBlank { PdfSourceRef.cleanCompositeLabel(debugLabel) }
            .ifBlank { PdfSourceRef.userVisibleSource(fallbackSource) }
        return label.takeIf { it.isNotBlank() }
    }

    private fun isAnswerAlignedEvidence(
        retrieval: RetrievalResult,
        extractiveAnswer: LocalExtractiveAnswer?
    ): Boolean {
        val item = retrieval.item ?: return false
        if (item.highlightHint.isNullOrBlank()) return false
        return when {
            extractiveAnswer?.retrievalId != null -> item.id == extractiveAnswer.retrievalId
            else -> true
        }
    }
}
