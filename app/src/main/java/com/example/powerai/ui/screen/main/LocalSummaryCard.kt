package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.LocalAnswerFeedback
import com.example.powerai.ui.screen.hybrid.LocalPageState
import com.example.powerai.ui.screen.hybrid.LocalSummaryState

/**
 * 卡片容器：负责标题、meta、support note 与内容分发；
 * 格式化逻辑见 [LocalSummaryCardDiagnosticsKt]（同包纯函数）、
 * discarded gate 见 [LocalDiscardedDiagnosticItem]、citation 见
 * [LocalCitationStrip]、内容/动作见 [LocalSummaryContent]。
 */
@Suppress("LongParameterList", "LongMethod")
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
    modifier: Modifier = Modifier,
) {
    if (!state.hasVisibleContent) return
    val summaryDisplayText =
        remember(state.summary, state.pageState, citationCount) {
            buildLocalSummaryDisplayText(
                summary = state.summary,
                pageState = state.pageState,
                citationCount = citationCount,
            )
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = state.title.ifBlank { defaultLocalSummaryTitle(state) },
                style = MaterialTheme.typography.titleMedium,
            )
            val showSupportNote =
                state.supportNote.isNotBlank() &&
                    state.pageState != LocalPageState.ANSWER_READY &&
                    state.pageState != LocalPageState.TOPIC_OVERVIEW
            val summaryMeta = buildLocalSummaryMeta(state)
            if (summaryMeta.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summaryMeta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                )
            }
            if (showSupportNote) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.supportNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                )
            }
            Spacer(modifier = Modifier.height(if (showSupportNote) 6.dp else 4.dp))

            LocalSummaryContent(
                state = state,
                summaryDisplayText = summaryDisplayText,
                feedback = feedback,
                onCopy = onCopy,
                onRetry = onRetry,
                onThumbUp = onThumbUp,
                onThumbDown = onThumbDown,
                onCitationClick = onCitationClick,
                citationCount = citationCount,
                onShowSources = onShowSources,
            )
        }
    }
}
