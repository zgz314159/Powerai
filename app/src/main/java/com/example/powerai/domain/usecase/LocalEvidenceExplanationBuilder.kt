package com.example.powerai.domain.usecase

import com.example.powerai.domain.query.QueryIntent
import java.util.Locale

internal object LocalEvidenceExplanationBuilder {

    fun buildDiscardExplanation(
        understanding: com.example.powerai.domain.query.QueryUnderstandingResult,
        normalized: String,
        anchors: List<String>,
        hasAnchor: Boolean,
        questionStyle: Boolean,
        qaStyle: Boolean,
        noisy: Boolean,
        tokenCoverage: Float,
        contextualSignal: Float
    ): LocalDiscardExplanation {
        if (noisy) {
            val markers = LocalEvidenceHeuristics.noisyMarkers.filter(normalized::contains).take(3)
            return LocalDiscardExplanation(
                reason = LocalDiscardReason.NOISY_MARKERS,
                detailCode = "matched_noisy_terms",
                detail = markers.joinToString(", ").ifBlank { "命中附件/巡视类噪声词" }
            )
        }
        if (understanding.signals.contains("definition_style") && !qaStyle && !questionStyle && !hasAnchor) {
            return LocalDiscardExplanation(
                reason = LocalDiscardReason.WEAK_DEFINITION_EVIDENCE,
                detailCode = "missing_definition_signals",
                detail = "missing definition_anchor, question_style, or qa_style"
            )
        }
        if ((tokenCoverage + contextualSignal) < 0.34f) {
            return LocalDiscardExplanation(
                reason = LocalDiscardReason.LOW_COVERAGE,
                detailCode = "coverage_below_threshold",
                detail = String.format(Locale.US, "coverage+ctx=%.2f < 0.34", tokenCoverage + contextualSignal)
            )
        }
        if (!hasAnchor && understanding.intent != QueryIntent.TOPIC_OVERVIEW) {
            return LocalDiscardExplanation(
                reason = LocalDiscardReason.MISSING_ANCHOR,
                detailCode = "expected_anchor_missing",
                detail = anchors.take(3).joinToString(", ").ifBlank { "无可用锚" }
            )
        }
        return LocalDiscardExplanation(
            reason = LocalDiscardReason.LOW_RELEVANCE,
            detailCode = "combined_signal_weak",
            detail = String.format(Locale.US, "coverage+ctx=%.2f", tokenCoverage + contextualSignal)
        )
    }

    fun buildThresholdSnapshot(
        understanding: com.example.powerai.domain.query.QueryUnderstandingResult,
        tokenCoverage: Float,
        contextualSignal: Float,
        anchorHits: Int,
        hasAnchor: Boolean,
        noisy: Boolean,
        questionStyle: Boolean,
        qaStyle: Boolean
    ): LocalDiscardThresholdSnapshot {
        val combinedSignal = tokenCoverage + contextualSignal
        val combinedThreshold = when (understanding.intent) {
            QueryIntent.TOPIC_OVERVIEW -> 0.34f
            QueryIntent.PROCEDURE -> 0.50f
            QueryIntent.COMPARISON -> 0.34f
            QueryIntent.FACT_QUESTION -> when {
                understanding.signals.contains("condition_style") -> 0.50f
                understanding.signals.contains("action_rule_style") -> 0.50f
                understanding.signals.contains("definition_style") -> 0.45f
                else -> 0.50f
            }
        }
        val anchorRequired = when (understanding.intent) {
            QueryIntent.TOPIC_OVERVIEW -> false
            QueryIntent.PROCEDURE -> true
            QueryIntent.COMPARISON -> true
            QueryIntent.FACT_QUESTION -> understanding.signals.contains("condition_style") || understanding.signals.contains("action_rule_style")
        }
        val definitionEvidenceRequired = understanding.signals.contains("definition_style")
        val definitionEvidencePassed = hasAnchor || questionStyle || qaStyle

        val gates = buildList {
            add(LocalDiscardRuleGateSnapshot("combined_signal", combinedSignal >= combinedThreshold, String.format(Locale.US, "%.2f", combinedSignal), String.format(Locale.US, "%.2f", combinedThreshold)))
            add(LocalDiscardRuleGateSnapshot("anchor_gate", !anchorRequired || anchorHits > 0, anchorHits.toString(), if (anchorRequired) ">=1" else "not_required"))
            add(LocalDiscardRuleGateSnapshot("definition_evidence", !definitionEvidenceRequired || definitionEvidencePassed, if (definitionEvidencePassed) "passed" else "none", if (definitionEvidenceRequired) "anchor|style" else "not_required"))
            add(LocalDiscardRuleGateSnapshot("noise_filter", !noisy, if (noisy) "matched" else "clean", "clean"))
        }

        return LocalDiscardThresholdSnapshot(
            combinedSignal = String.format(Locale.US, "%.2f", combinedSignal),
            combinedThreshold = String.format(Locale.US, "%.2f", combinedThreshold),
            anchorHits = anchorHits,
            anchorRequired = anchorRequired,
            primaryFailedGate = gates.find { !it.passed }?.code.orEmpty(),
            failedGateCount = gates.count { !it.passed },
            gates = gates
        )
    }
}
