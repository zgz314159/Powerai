package com.example.powerai.ui.screen.hybrid

import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.domain.query.QueryUnderstandingPipeline
import com.example.powerai.domain.usecase.LocalEvidenceAssessment
import com.example.powerai.domain.usecase.LocalDiscardedCandidate
import com.example.powerai.domain.usecase.LocalSearchQueryVariantBuilder
import java.util.Locale

internal object LocalDiagnosticsFormatter {
    fun build(
        query: String,
        assessment: LocalEvidenceAssessment,
        retrievals: List<RetrievalResult>,
        totalCandidateCount: Int,
        refinedCandidateCount: Int,
        discardedCandidates: List<LocalDiscardedCandidate> = emptyList()
    ): String {
        val understanding = QueryUnderstandingPipeline.understand(query)
        val lines = mutableListOf<String>()
        lines += "检索与判定明细"
        lines += "query=${query.ifBlank { "(empty)" }}"
        lines += "normalized=${understanding.normalizedQuery.ifBlank { "(empty)" }}"
        lines += "understanding_intent=${understanding.intent.name}"
        lines += "retrieval_queries=${understanding.retrievalQueries.joinToString(" || ").ifBlank { "(none)" }}"
        lines += "query_plan=${LocalSearchQueryVariantBuilder.buildPlan(query).take(6).joinToString(" || ") { "${it.strategy}:${it.text}" }.ifBlank { "(none)" }}"
        lines += "pipeline raw=$totalCandidateCount refined=$refinedCandidateCount displayed=${retrievals.size}"
        val selectedVariant = retrievals.firstOrNull()?.debug?.get("query_variant")?.toString().orEmpty()
        val selectedVariantHits = retrievals.firstOrNull()?.debug?.get("query_variant_hit_count")?.toString().orEmpty()
        val selectedPlanStrategy = retrievals.firstOrNull()?.debug?.get("query_plan_strategy")?.toString().orEmpty()
        if (selectedVariant.isNotBlank()) {
            lines += buildString {
                append("selected_variant=")
                append(selectedVariant)
                if (selectedPlanStrategy.isNotBlank()) {
                    append(" strategy=")
                    append(selectedPlanStrategy)
                }
                if (selectedVariantHits.isNotBlank()) {
                    append(" hits=")
                    append(selectedVariantHits)
                }
            }
        }
        lines += "decision=${assessment.decision.name} intent=${assessment.intent.name}"
        lines += "reason=${assessment.reason}"
        lines += buildString {
            append("scores primary=")
            append(formatScore(assessment.primaryScore))
            append(" agg=")
            append(formatScore(assessment.aggregateScore))
            append(" strong=")
            append(assessment.strongEvidenceCount)
            append(" exact=")
            append(assessment.exactMatchCount)
            append(" sources=")
            append(assessment.distinctSourceCount)
            append(" evidence=")
            append(assessment.evidenceCount)
        }

        if (retrievals.isEmpty()) {
            lines += "hits=(none)"
        } else {
            retrievals.take(3).forEachIndexed { index, result ->
                val title = result.item?.title ?: result.metadata["title"].orEmpty().ifBlank { "(无标" }
                val source = result.item?.source ?: result.metadata["source"] ?: result.source.ifBlank { "unknown" }
                val variant = result.debug?.get("query_variant")?.toString()
                val variantHits = result.debug?.get("query_variant_hit_count")?.toString()
                val planStrategy = result.debug?.get("query_plan_strategy")?.toString()
                val rerankScore = result.debug?.get("rerank_score")?.toString()
                val contextualSignal = result.debug?.get("contextual_signal")?.toString()
                val contextLabel = result.debug?.get("context_label")?.toString()
                val qaStyle = result.debug?.get("qa_style")?.toString()
                lines += buildString {
                    append("top")
                    append(index + 1)
                    append(": score=")
                    append(formatScore(result.confidence ?: result.score))
                    if (!rerankScore.isNullOrBlank()) {
                        append(" rerank=")
                        append(rerankScore)
                    }
                    if (!contextualSignal.isNullOrBlank()) {
                        append(" ctx=")
                        append(contextualSignal)
                    }
                    append(" source=")
                    append(source)
                    append(" title=")
                    append(title)
                    if (!contextLabel.isNullOrBlank()) {
                        append(" context=")
                        append(contextLabel)
                    }
                    if (!variant.isNullOrBlank()) {
                        append(" variant=")
                        append(variant)
                    }
                    if (!planStrategy.isNullOrBlank()) {
                        append(" strategy=")
                        append(planStrategy)
                    }
                    if (!variantHits.isNullOrBlank()) {
                        append(" hits=")
                        append(variantHits)
                    }
                    if (!qaStyle.isNullOrBlank()) {
                        append(" qa=")
                        append(qaStyle)
                    }
                }
            }
        }

        if (discardedCandidates.isNotEmpty()) {
            discardedCandidates.take(3).forEachIndexed { index, candidate ->
                lines += buildString {
                    append("drop")
                    append(index + 1)
                    append(": reason_code=")
                    append(candidate.reason.code)
                    append(" reason=")
                    append(candidate.reason.label)
                    if (candidate.reasonDetailCode.isNotBlank()) {
                        append(" detail_code=")
                        append(candidate.reasonDetailCode)
                    }
                    if (candidate.reasonDetail.isNotBlank()) {
                        append(" detail=")
                        append(candidate.reasonDetail)
                    }
                    if (candidate.thresholdSnapshot.combinedSignal.isNotBlank()) {
                        append(" combined=")
                        append(candidate.thresholdSnapshot.combinedSignal)
                        append("/")
                        append(candidate.thresholdSnapshot.combinedThreshold)
                    }
                    append(" anchor=")
                    append(candidate.thresholdSnapshot.anchorHits)
                    append("/")
                    append(if (candidate.thresholdSnapshot.anchorRequired) 1 else 0)
                    if (candidate.thresholdSnapshot.primaryFailedGate.isNotBlank()) {
                        append(" failed=")
                        append(LocalDiagnosticGateLabelFormatter.labelWithCode(candidate.thresholdSnapshot.primaryFailedGate))
                        append("+")
                        append(candidate.thresholdSnapshot.failedGateCount)
                    }
                    if (candidate.thresholdSnapshot.gates.isNotEmpty()) {
                        append(" gates=")
                        append(
                            candidate.thresholdSnapshot.gates.joinToString("|") { gate ->
                                val status = if (gate.passed) "pass" else "fail"
                                "${LocalDiagnosticGateLabelFormatter.labelWithCode(gate.code)}:$status(${gate.actual}->${gate.expected})"
                            }
                        )
                    }
                    if (candidate.tokenCoverage.isNotBlank()) {
                        append(" coverage=")
                        append(candidate.tokenCoverage)
                    }
                    if (candidate.contextualSignal.isNotBlank()) {
                        append(" ctx=")
                        append(candidate.contextualSignal)
                    }
                    if (candidate.source.isNotBlank()) {
                        append(" source=")
                        append(candidate.source)
                    }
                    if (candidate.title.isNotBlank()) {
                        append(" title=")
                        append(candidate.title)
                    }
                }
            }
        }

        return lines.joinToString("\n")
    }

    private fun formatScore(value: Float): String = String.format(Locale.US, "%.2f", value)
}
