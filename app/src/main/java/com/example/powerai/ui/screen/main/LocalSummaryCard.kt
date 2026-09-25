package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.LocalAnswerFeedback
import com.example.powerai.ui.screen.hybrid.LocalDiagnosticSnapshot
import com.example.powerai.ui.screen.hybrid.LocalDiagnosticGateLabelFormatter
import com.example.powerai.ui.screen.hybrid.LocalDiscardedDiagnosticHit
import com.example.powerai.ui.screen.hybrid.LocalPageState
import com.example.powerai.ui.screen.hybrid.LocalQueryIntent
import com.example.powerai.ui.screen.hybrid.LocalSummaryState

@Composable
internal fun LocalSummaryCard(
    state: LocalSummaryState,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
    feedback: LocalAnswerFeedback = LocalAnswerFeedback.NONE,
    onThumbUp: () -> Unit = {},
    onThumbDown: () -> Unit = {},
    onCitationClick: ((Int) -> Unit)? = null,
    citationCount: Int = 0,
    onShowSources: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!state.hasVisibleContent) return
    val summaryDisplayText = remember(state.summary, state.pageState, citationCount) {
        buildLocalSummaryDisplayText(
            summary = state.summary,
            pageState = state.pageState,
            citationCount = citationCount
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = state.title.ifBlank { defaultLocalSummaryTitle(state) },
                style = MaterialTheme.typography.titleMedium
            )
            val showSupportNote = state.supportNote.isNotBlank() &&
                state.pageState != LocalPageState.ANSWER_READY &&
                state.pageState != LocalPageState.TOPIC_OVERVIEW
            val summaryMeta = buildLocalSummaryMeta(state)
            if (summaryMeta.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summaryMeta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                )
            }
            if (showSupportNote) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.supportNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                )
            }
            Spacer(modifier = Modifier.height(if (showSupportNote) 6.dp else 4.dp))

            when {
                state.pageState == LocalPageState.SEARCHING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = " 正在整理相关内容...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                state.isStreaming && state.summary.isBlank() -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = " 正在生成回答...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                state.summary.isNotBlank() -> {
                    ResponseBody(
                        text = summaryDisplayText,
                        isLoading = state.isStreaming,
                        onCopy = { onCopy(summaryDisplayText) },
                        onRetry = onRetry,
                        allowRetry = state.query.isNotBlank(),
                        onThumbUp = onThumbUp,
                        onThumbDown = onThumbDown,
                        isThumbUpSelected = feedback == LocalAnswerFeedback.UP,
                        isThumbDownSelected = feedback == LocalAnswerFeedback.DOWN,
                        onShowSources = onShowSources?.takeIf { citationCount > 0 },
                        onCitationClick = onCitationClick,
                        renderMarkdownWhenPossible = true,
                        supplementalContent = if (shouldShowLocalCitationStrip(state, citationCount)) {
                            {
                                LocalCitationStrip(
                                    citationCount = citationCount,
                                    onCitationClick = onCitationClick
                                )
                            }
                        } else {
                            null
                        }
                    )
                }

                else -> {
                    Text(
                        text = fallbackLocalSummaryText(state),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalDiscardedDiagnosticItem(
    index: Int,
    hit: LocalDiscardedDiagnosticHit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(index, hit.title, hit.reasonCode, hit.reasonDetailCode) {
        mutableStateOf(false)
    }

    Text(
        text = buildDiscardedDiagnosticSummaryLine(index, hit),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
    if (hit.gateSummaries.isNotEmpty()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = modifier.padding(start = 8.dp)
        ) {
            Text(buildDiscardedGateToggleLabel(hit.primaryFailedGate, hit.failedGateCount, hit.gateSummaries.size, expanded))
        }
        if (expanded) {
            Text(
                text = buildDiscardedDiagnosticGateDetailLine(hit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(start = 8.dp)
            )
        }
    }
}

private fun buildLocalSummaryMeta(state: LocalSummaryState): String {
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

private fun defaultLocalSummaryTitle(state: LocalSummaryState): String = when (state.pageState) {
    LocalPageState.SEARCHING -> "正在查找相关内容"
    LocalPageState.ANSWER_READY -> "回答"
    LocalPageState.TOPIC_OVERVIEW -> "概览"
    LocalPageState.INSUFFICIENT_EVIDENCE -> "资料不足"
    LocalPageState.ERROR -> "暂时无法生成回答"
    LocalPageState.EVIDENCE_ONLY -> "参考资料"
    LocalPageState.IDLE -> ""
}

private fun buildLocalDiagnosticSummaryLines(snapshot: LocalDiagnosticSnapshot): List<String> {
    val evidence = listOf(snapshot.topEvidenceSource, snapshot.topEvidenceTitle)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    return buildList {
        if (snapshot.queryPlanPreview.isNotBlank()) {
            add("计划：${snapshot.queryPlanPreview}")
        }
        if (snapshot.queryPlanStrategy.isNotBlank() || snapshot.queryVariant.isNotBlank()) {
            add(buildString {
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
            })
        }
        if (evidence.isNotBlank()) {
            add("命中：$evidence")
        }
        if (snapshot.topEvidencePreview.isNotBlank()) {
            add("摘要：${snapshot.topEvidencePreview}")
        }
        snapshot.topHits.forEach { hit ->
            add(buildString {
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
            })
        }
    }
}

internal fun buildDiscardedDiagnosticSummaryLine(
    index: Int,
    hit: LocalDiscardedDiagnosticHit
): String = buildString {
    append("drop")
    append(index + 1)
    append("：")
    append(hit.reasonCode.ifBlank { "low_relevance" })
    append("(")
    append(hit.reason.ifBlank { "综合相关性不足" })
    append(")")
    if (hit.reasonDetailCode.isNotBlank()) {
        append(" detail=")
        append(hit.reasonDetailCode)
    }
    if (hit.reasonDetail.isNotBlank()) {
        append("[")
        append(hit.reasonDetail)
        append("]")
    }
    if (hit.combinedSignal.isNotBlank()) {
        append(" combined=")
        append(hit.combinedSignal)
        append("/")
        append(hit.combinedThreshold.ifBlank { "?" })
    }
    if (hit.anchorHits.isNotBlank()) {
        append(" anchor=")
        append(hit.anchorHits)
        append("/")
        append(if (hit.anchorRequired == "true") "1" else "0")
    }
    if (hit.primaryFailedGate.isNotBlank()) {
        append(" failed=")
        append(LocalDiagnosticGateLabelFormatter.labelWithCode(hit.primaryFailedGate))
        if (hit.failedGateCount.isNotBlank()) {
            append("+")
            append(hit.failedGateCount)
        }
    }
    if (hit.tokenCoverage.isNotBlank()) {
        append(" coverage=")
        append(hit.tokenCoverage)
    }
    if (hit.contextualSignal.isNotBlank()) {
        append(" ctx=")
        append(hit.contextualSignal)
    }
    if (hit.source.isNotBlank()) {
        append(" · ")
        append(hit.source)
    }
    if (hit.title.isNotBlank()) {
        append(" · ")
        append(hit.title)
    }
}

internal fun buildDiscardedDiagnosticGateDetailLine(hit: LocalDiscardedDiagnosticHit): String = buildString {
    append("gate详情：")
    append(
        hit.gateSummaries.joinToString(" | ") { gate ->
            buildString {
                append(LocalDiagnosticGateLabelFormatter.labelWithCode(gate.code))
                append(":")
                append(if (gate.passed == "true") "pass" else "fail")
                if (gate.actual.isNotBlank() || gate.expected.isNotBlank()) {
                    append("(actual=")
                    append(gate.actual.ifBlank { "?" })
                    append(", expected=")
                    append(gate.expected.ifBlank { "?" })
                    append(")")
                }
            }
        }
    )
}

internal fun buildDiscardedGateToggleLabel(
    primaryFailedGate: String,
    failedGateCount: String,
    gateCount: Int,
    expanded: Boolean
): String {
    val normalizedFailedCount = failedGateCount.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val normalizedCount = gateCount.coerceAtLeast(0)
    val action = if (expanded) "收起" else "展开"
    val primaryLabel = LocalDiagnosticGateLabelFormatter.label(primaryFailedGate)
    return when {
        primaryLabel.isNotBlank() && normalizedFailedCount > 0 && normalizedCount > 0 -> {
            "$action $primaryLabel 等 $normalizedFailedCount 个失败 gate 详情（共 $normalizedCount 个 gate）"
        }
        primaryLabel.isNotBlank() && normalizedFailedCount > 0 -> "$action $primaryLabel 等 $normalizedFailedCount 个失败 gate 详情"
        normalizedFailedCount > 0 && normalizedCount > 0 -> "$action $normalizedFailedCount 个失败 gate 详情（共 $normalizedCount 个 gate）"
        normalizedFailedCount > 0 -> "$action $normalizedFailedCount 个失败 gate 详情"
        normalizedCount > 0 -> "$action $normalizedCount 个 gate 详情"
        else -> "$action gate 详情"
    }
}

private fun fallbackLocalSummaryText(state: LocalSummaryState): String = when (state.pageState) {
    LocalPageState.SEARCHING -> "正在整理相关内容，请稍候。"
    LocalPageState.EVIDENCE_ONLY -> "已找到 ${state.evidenceCount} 条相关资料，可先查看下方内容。"
    LocalPageState.INSUFFICIENT_EVIDENCE -> state.errorMessage
        ?: "当前找到的相关内容还不够，建议换个更具体的问法试试。"
    LocalPageState.ERROR -> state.errorMessage ?: "暂时没能生成回答，请稍后重试。"
    LocalPageState.ANSWER_READY,
    LocalPageState.TOPIC_OVERVIEW,
    LocalPageState.IDLE -> ""
}

@Composable
private fun LocalCitationStrip(
    citationCount: Int,
    onCitationClick: ((Int) -> Unit)?
) {
    if (citationCount <= 0 || onCitationClick == null) return

    val maxCount = citationCount.coerceAtMost(6)
    Column(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
        Text(
            text = "相关来源",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row {
            for (number in 1..maxCount) {
                TextButton(onClick = { onCitationClick(number) }) {
                    Text(text = "[$number]")
                }
            }
        }
    }
}

private fun shouldShowLocalCitationStrip(state: LocalSummaryState, citationCount: Int): Boolean {
    if (citationCount <= 0) return false
    return state.pageState == LocalPageState.ANSWER_READY || state.pageState == LocalPageState.TOPIC_OVERVIEW
}

private fun buildLocalSummaryDisplayText(
    summary: String,
    pageState: LocalPageState,
    citationCount: Int
): String {
    if (summary.isBlank()) return summary
    if (citationCount <= 0) return summary
    if (pageState != LocalPageState.ANSWER_READY && pageState != LocalPageState.TOPIC_OVERVIEW) return summary
    if (Regex("\\[\\d{1,2}]", RegexOption.MULTILINE).containsMatchIn(summary)) return summary

    val citations = (1..citationCount.coerceAtMost(6)).joinToString(separator = "") { index -> "[$index]" }
    return buildString {
        append(summary.trimEnd())
        append("\n\n相关来源：")
        append(citations)
    }
}