package com.example.powerai.ui.screen.hybrid

import com.example.powerai.domain.usecase.LocalAnswerDecision
import com.example.powerai.domain.usecase.LocalAnswerIntent
import com.example.powerai.domain.usecase.LocalEvidenceAssessment
import com.example.powerai.domain.usecase.LocalExtractiveAnswer

internal object LocalSummaryStateFactory {
    fun initial(query: String, intent: LocalAnswerIntent): LocalSummaryState {
        return LocalSummaryState(
            pageState = LocalPageState.SEARCHING,
            query = query,
            intent = intent.toUiIntent(),
            title = "正在查找相关内容",
            supportNote = "会先看看找到的资料，再决定是否生成回答。",
            summary = "",
            isStreaming = false,
            canRunGemma = false
        )
    }

    fun fromAssessment(
        query: String,
        assessment: LocalEvidenceAssessment,
        retrievalCount: Int,
        totalEvidenceCount: Int,
        gemmaRunning: Boolean,
        diagnosticSnapshot: LocalDiagnosticSnapshot = LocalDiagnosticSnapshot(),
        diagnostics: String = "",
        extractiveAnswer: LocalExtractiveAnswer? = null
    ): LocalSummaryState {
        val queryIntent = assessment.intent.toUiIntent()
        val extractiveSummary = extractiveAnswer?.toDisplayText().orEmpty()
        return when {
            assessment.decision == LocalAnswerDecision.INSUFFICIENT_EVIDENCE -> LocalSummaryState(
                pageState = LocalPageState.INSUFFICIENT_EVIDENCE,
                query = query,
                intent = queryIntent,
                title = "资料不足",
                supportNote = supportNote(assessment),
                diagnosticSnapshot = diagnosticSnapshot,
                diagnostics = diagnostics,
                summary = insufficientSummary(retrievalCount),
                evidenceCount = retrievalCount,
                totalEvidenceCount = totalEvidenceCount,
                isStreaming = false,
                canRunGemma = false,
                errorMessage = null
            )

            gemmaRunning -> LocalSummaryState(
                pageState = LocalPageState.EVIDENCE_ONLY,
                query = query,
                intent = queryIntent,
                title = streamingTitle(queryIntent),
                supportNote = supportNote(assessment, hasExtractiveAnswer = extractiveAnswer != null),
                diagnosticSnapshot = diagnosticSnapshot,
                diagnostics = diagnostics,
                summary = extractiveSummary,
                evidenceCount = retrievalCount,
                totalEvidenceCount = totalEvidenceCount,
                isStreaming = true,
                canRunGemma = true,
                errorMessage = null
            )

            else -> LocalSummaryState(
                pageState = LocalPageState.EVIDENCE_ONLY,
                query = query,
                intent = queryIntent,
                title = if (extractiveSummary.isBlank()) "参考资料" else "原文摘录",
                supportNote = supportNote(assessment, hasExtractiveAnswer = extractiveAnswer != null),
                diagnosticSnapshot = diagnosticSnapshot,
                diagnostics = diagnostics,
                summary = extractiveSummary.ifBlank { fallbackSummary(queryIntent, retrievalCount) },
                evidenceCount = retrievalCount,
                totalEvidenceCount = totalEvidenceCount,
                isStreaming = false,
                canRunGemma = false,
                errorMessage = null
            )
        }
    }

    fun applyGemmaResult(previous: LocalSummaryState, text: String): LocalSummaryState {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return finishGemmaWithoutResult(previous)
        }

        return previous.copy(
            pageState = when (previous.intent) {
                LocalQueryIntent.FACT_QUESTION -> LocalPageState.ANSWER_READY
                LocalQueryIntent.PROCEDURE -> LocalPageState.ANSWER_READY
                LocalQueryIntent.COMPARISON -> LocalPageState.ANSWER_READY
                LocalQueryIntent.TOPIC_OVERVIEW -> LocalPageState.TOPIC_OVERVIEW
            },
            title = finalTitle(previous.intent),
            supportNote = if (previous.canRunGemma) "下面这段回答是根据当前找到的资料整理的。" else previous.supportNote,
            diagnostics = previous.diagnostics,
            summary = trimmed,
            isStreaming = false,
            canRunGemma = true,
            errorMessage = null
        )
    }

    fun finishGemmaWithoutResult(previous: LocalSummaryState): LocalSummaryState {
        val fallbackText = when {
            previous.summary.isNotBlank() -> previous.summary
            previous.evidenceCount > 0 -> fallbackSummary(previous.intent, previous.evidenceCount)
            else -> "当前没有足够稳定的本地证据来生成回答。"
        }
        return previous.copy(
            pageState = if (previous.evidenceCount > 0) LocalPageState.EVIDENCE_ONLY else LocalPageState.INSUFFICIENT_EVIDENCE,
            title = if (previous.evidenceCount > 0) {
                if (previous.summary.isBlank()) "参考资料" else "原文摘录"
            } else {
                "资料不足"
            },
            supportNote = if (previous.evidenceCount > 0) {
                "这次先把最相关的原文给你，回答整理没有稳定完成。"
            } else {
                "这次没有找到足够稳定的本地证据。"
            },
            summary = fallbackText,
            isStreaming = false,
            canRunGemma = previous.canRunGemma,
            errorMessage = null
        )
    }

    private fun LocalAnswerIntent.toUiIntent(): LocalQueryIntent = when (this) {
        LocalAnswerIntent.FACT_QUESTION -> LocalQueryIntent.FACT_QUESTION
        LocalAnswerIntent.TOPIC_OVERVIEW -> LocalQueryIntent.TOPIC_OVERVIEW
        LocalAnswerIntent.PROCEDURE -> LocalQueryIntent.PROCEDURE
        LocalAnswerIntent.COMPARISON -> LocalQueryIntent.COMPARISON
    }

    private fun streamingTitle(intent: LocalQueryIntent): String = when (intent) {
        LocalQueryIntent.FACT_QUESTION -> "正在整理回答"
        LocalQueryIntent.TOPIC_OVERVIEW -> "正在整理概览"
        LocalQueryIntent.PROCEDURE -> "正在整理流程"
        LocalQueryIntent.COMPARISON -> "正在整理对比"
    }

    private fun finalTitle(intent: LocalQueryIntent): String = when (intent) {
        LocalQueryIntent.FACT_QUESTION -> "回答"
        LocalQueryIntent.TOPIC_OVERVIEW -> "概览"
        LocalQueryIntent.PROCEDURE -> "流程"
        LocalQueryIntent.COMPARISON -> "对比"
    }

    private fun fallbackSummary(intent: LocalQueryIntent, evidenceCount: Int): String = when (intent) {
        LocalQueryIntent.FACT_QUESTION -> "已找到 ${evidenceCount} 条相关资料，可以先看看原文。"
        LocalQueryIntent.TOPIC_OVERVIEW -> "已找到 ${evidenceCount} 条相关资料，可以先看看原文。"
        LocalQueryIntent.PROCEDURE -> "已找到 ${evidenceCount} 条步骤相关资料，可以先看看原文。"
        LocalQueryIntent.COMPARISON -> "已找到 ${evidenceCount} 条对比相关资料，可以先看看原文。"
    }

    private fun insufficientSummary(retrievalCount: Int): String {
        return if (retrievalCount == 0) {
            "没找到可直接回答的问题证据，换个关键词试试。"
        } else {
            "已找到 ${retrievalCount} 条内容，但还不足以下结论，建议换更具体的问法。"
        }
    }

    private fun supportNote(
        assessment: LocalEvidenceAssessment,
        hasExtractiveAnswer: Boolean = false
    ): String = when (assessment.decision) {
        LocalAnswerDecision.USE_GEMMA_ANSWER -> when (assessment.intent) {
            LocalAnswerIntent.FACT_QUESTION -> buildString {
                append("找到的内容已经比较够了，正在整理回答")
                if (assessment.exactMatchCount > 0) {
                    append("，已经对上关键条文")
                } else if (assessment.strongEvidenceCount > 0) {
                    append("，前面的资料更贴近你的问题")
                }
                if (hasExtractiveAnswer) {
                    append("，也摘出了关键原文")
                }
                append("。")
            }

            LocalAnswerIntent.TOPIC_OVERVIEW -> buildString {
                append("找到的资料已经够用了，正在整理概览")
                if (assessment.distinctSourceCount >= 2) {
                    append("，内容来自多个来源")
                }
                if (hasExtractiveAnswer) {
                    append("，也摘出了关键原文")
                }
                append("。")
            }

            LocalAnswerIntent.PROCEDURE -> buildString {
                append("步骤相关的内容已经够用了，正在整理流程")
                if (assessment.strongEvidenceCount > 0) {
                    append("，已经找到关键步骤")
                }
                if (hasExtractiveAnswer) {
                    append("，也摘出了关键原文")
                }
                append("。")
            }

            LocalAnswerIntent.COMPARISON -> buildString {
                append("对比相关的内容已经够用了，正在整理差异")
                if (assessment.distinctSourceCount >= 2) {
                    append("，内容来自多个来源")
                }
                if (hasExtractiveAnswer) {
                    append("，也摘出了关键原文")
                }
                append("。")
            }
        }

        LocalAnswerDecision.SHOW_EVIDENCE_ONLY -> when (assessment.intent) {
            LocalAnswerIntent.FACT_QUESTION -> if (hasExtractiveAnswer) "先把最相关的原文给你，暂时不直接下结论。" else "先把相关资料给你，你也可以先看看原文。"
            LocalAnswerIntent.TOPIC_OVERVIEW -> if (hasExtractiveAnswer) "先把最相关的原文给你，暂时不自动概括。" else "先把相关资料给你，暂时不自动概括。"
            LocalAnswerIntent.PROCEDURE -> if (hasExtractiveAnswer) "先把关键步骤原文给你，暂时不自动整理流程。" else "先把步骤相关资料给你，暂时不自动整理流程。"
            LocalAnswerIntent.COMPARISON -> if (hasExtractiveAnswer) "先把关键对比原文给你，暂时不自动总结差异。" else "先把对比相关资料给你，暂时不自动总结差异。"
        }

        LocalAnswerDecision.INSUFFICIENT_EVIDENCE -> when {
            assessment.evidenceCount == 0 -> "还没找到足够相关的资料。"
            assessment.strongEvidenceCount == 0 -> "找到了一些内容，但还不够贴近你的问题。"
            else -> "已经找到一些相关资料，但还不够支撑结论。"
        }
    }
}