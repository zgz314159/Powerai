package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.LocalAnswerFeedback
import com.example.powerai.ui.screen.hybrid.LocalPageState
import com.example.powerai.ui.screen.hybrid.LocalSummaryState

/**
 * Content/actions dispatcher for [LocalSummaryCard]: loading rows, the
 * streaming summary with copy/retry/thumb actions, and the fallback text.
 */
@Suppress("LongParameterList", "ktlint:standard:function-naming")
@Composable
internal fun LocalSummaryContent(
    state: LocalSummaryState,
    summaryDisplayText: String,
    feedback: LocalAnswerFeedback,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    onCitationClick: ((Int) -> Unit)?,
    citationCount: Int,
    onShowSources: (() -> Unit)?,
) {
    when {
        state.pageState == LocalPageState.SEARCHING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = " 正在整理相关内容...",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        state.isStreaming && state.summary.isBlank() -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    text = " 正在生成回答...",
                    style = MaterialTheme.typography.bodyMedium,
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
                supplementalContent =
                    if (shouldShowLocalCitationStrip(state, citationCount)) {
                        {
                            LocalCitationStrip(
                                citationCount = citationCount,
                                onCitationClick = onCitationClick,
                            )
                        }
                    } else {
                        null
                    },
            )
        }

        else -> {
            Text(
                text = fallbackLocalSummaryText(state),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
