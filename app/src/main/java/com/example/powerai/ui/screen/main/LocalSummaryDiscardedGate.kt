package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.screen.hybrid.LocalDiagnosticGateLabelFormatter
import com.example.powerai.ui.screen.hybrid.LocalDiscardedDiagnosticHit

@Suppress("UnusedPrivateMember", "ktlint:standard:function-naming")
@Composable
private fun LocalDiscardedDiagnosticItem(
    index: Int,
    hit: LocalDiscardedDiagnosticHit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(index, hit.title, hit.reasonCode, hit.reasonDetailCode) {
        mutableStateOf(false)
    }

    Text(
        text = buildDiscardedDiagnosticSummaryLine(index, hit),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
    if (hit.gateSummaries.isNotEmpty()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = modifier.padding(start = 8.dp),
        ) {
            Text(buildDiscardedGateToggleLabel(hit.primaryFailedGate, hit.failedGateCount, hit.gateSummaries.size, expanded))
        }
        if (expanded) {
            Text(
                text = buildDiscardedDiagnosticGateDetailLine(hit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(start = 8.dp),
            )
        }
    }
}

internal fun buildDiscardedDiagnosticSummaryLine(
    index: Int,
    hit: LocalDiscardedDiagnosticHit,
): String =
    buildString {
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

internal fun buildDiscardedDiagnosticGateDetailLine(hit: LocalDiscardedDiagnosticHit): String =
    buildString {
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
            },
        )
    }

internal fun buildDiscardedGateToggleLabel(
    primaryFailedGate: String,
    failedGateCount: String,
    gateCount: Int,
    expanded: Boolean,
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
