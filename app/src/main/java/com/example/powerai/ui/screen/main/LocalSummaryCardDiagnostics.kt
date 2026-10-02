package com.example.powerai.ui.screen.main

import com.example.powerai.ui.screen.hybrid.HybridUiState
import com.example.powerai.ui.screen.hybrid.LocalDiagnosticSnapshot
import com.example.powerai.ui.screen.hybrid.LocalPageState
import com.example.powerai.ui.screen.hybrid.LocalSummaryState

/**
 * Visible failure copy for the "本地" page, or `null` when the state is not a
 * search failure (a genuine zero-hit result renders the ordinary empty state).
 */
internal fun localResultsFailureMessage(state: HybridUiState): String? =
    state.localSummaryState
        .takeIf { it.pageState == LocalPageState.ERROR }
        ?.let { fallbackLocalSummaryText(it) }

internal fun buildLocalSummaryMeta(state: LocalSummaryState): String {
    if (state.evidenceCount <= 0) return ""
    return buildString {
        append("参考了 ")
        append(state.evidenceCount)
        append(" 条资料")
        if (state.totalEvidenceCount > state.evidenceCount) {
            append("（共 ")
            append(state.totalEvidenceCount)
            append(" 条）")
        }
    }
}

internal fun defaultLocalSummaryTitle(state: LocalSummaryState): String =
    when (state.pageState) {
        LocalPageState.SEARCHING -> "正在查找相关内容"
        LocalPageState.ANSWER_READY -> "回答"
        LocalPageState.TOPIC_OVERVIEW -> "概览"
        LocalPageState.INSUFFICIENT_EVIDENCE -> "资料不足"
        LocalPageState.ERROR -> "搜索失败"
        LocalPageState.EVIDENCE_ONLY -> "参考资料"
        LocalPageState.IDLE -> ""
    }

internal fun fallbackLocalSummaryText(state: LocalSummaryState): String =
    when (state.pageState) {
        LocalPageState.SEARCHING -> "正在整理相关内容，请稍候。"
        LocalPageState.EVIDENCE_ONLY -> "已找到 ${state.evidenceCount} 条相关资料，可先查看下方内容。"
        LocalPageState.INSUFFICIENT_EVIDENCE ->
            state.errorMessage
                ?: "当前找到的相关内容还不够，建议换个更具体的问法试试。"
        LocalPageState.ERROR -> state.errorMessage ?: "本地检索暂时不可用，请重试。"
        LocalPageState.ANSWER_READY,
        LocalPageState.TOPIC_OVERVIEW,
        LocalPageState.IDLE,
        -> ""
    }

@Suppress("CyclomaticComplexMethod", "UnusedPrivateMember")
private fun buildLocalDiagnosticSummaryLines(snapshot: LocalDiagnosticSnapshot): List<String> {
    val evidence =
        listOf(snapshot.topEvidenceSource, snapshot.topEvidenceTitle)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    return buildList {
        if (snapshot.queryPlanPreview.isNotBlank()) {
            add("计划：${snapshot.queryPlanPreview}")
        }
        if (snapshot.queryPlanStrategy.isNotBlank() || snapshot.queryVariant.isNotBlank()) {
            add(
                buildString {
                    append("选中：")
                    append(snapshot.queryPlanStrategy.ifBlank { "(unknown)" })
                    if (snapshot.queryVariant.isNotBlank()) {
                        append(" · ")
                        append(snapshot.queryVariant)
                    }
                    if (snapshot.queryVariantHitCount.isNotBlank()) {
                        append(" · hits=")
                        append(snapshot.queryVariantHitCount)
                    }
                },
            )
        }
        if (evidence.isNotBlank()) {
            add("命中：$evidence")
        }
        if (snapshot.topEvidencePreview.isNotBlank()) {
            add("摘要：${snapshot.topEvidencePreview}")
        }
        snapshot.topHits.forEach { hit ->
            add(
                buildString {
                    append("top")
                    append(hit.rank)
                    append("：")
                    append(hit.score)
                    if (hit.rerankScore.isNotBlank()) {
                        append(" rerank=")
                        append(hit.rerankScore)
                    }
                    if (hit.contextualSignal.isNotBlank()) {
                        append(" ctx=")
                        append(hit.contextualSignal)
                    }
                    if (hit.anchorHits.isNotBlank()) {
                        append(" anchor=")
                        append(hit.anchorHits)
                    }
                    if (hit.qaStyle.isNotBlank()) {
                        append(" qa=")
                        append(hit.qaStyle)
                    }
                    if (hit.source.isNotBlank()) {
                        append(" · ")
                        append(hit.source)
                    }
                    if (hit.title.isNotBlank()) {
                        append(" · ")
                        append(hit.title)
                    }
                },
            )
        }
    }
}
