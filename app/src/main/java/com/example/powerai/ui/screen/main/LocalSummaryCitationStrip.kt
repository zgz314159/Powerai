package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.screen.hybrid.LocalPageState
import com.example.powerai.ui.screen.hybrid.LocalSummaryState

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun LocalCitationStrip(
    citationCount: Int,
    onCitationClick: ((Int) -> Unit)?,
) {
    if (citationCount <= 0 || onCitationClick == null) return

    val maxCount = citationCount.coerceAtMost(MAX_VISIBLE_CITATIONS)
    Column(modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)) {
        Text(
            text = "相关来源",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
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

internal fun shouldShowLocalCitationStrip(
    state: LocalSummaryState,
    citationCount: Int,
): Boolean {
    if (citationCount <= 0) return false
    return state.pageState == LocalPageState.ANSWER_READY || state.pageState == LocalPageState.TOPIC_OVERVIEW
}

@Suppress("ReturnCount", "MagicNumber")
internal fun buildLocalSummaryDisplayText(
    summary: String,
    pageState: LocalPageState,
    citationCount: Int,
): String {
    if (summary.isBlank()) return summary
    if (citationCount <= 0) return summary
    if (pageState != LocalPageState.ANSWER_READY && pageState != LocalPageState.TOPIC_OVERVIEW) return summary
    if (Regex("\\[\\d{1,2}]", RegexOption.MULTILINE).containsMatchIn(summary)) return summary

    val citations = (1..citationCount.coerceAtMost(MAX_VISIBLE_CITATIONS)).joinToString(separator = "") { index -> "[$index]" }
    return buildString {
        append(summary.trimEnd())
        append("\n\n相关来源：")
        append(citations)
    }
}

@Suppress("MagicNumber")
private const val MAX_VISIBLE_CITATIONS = 6
