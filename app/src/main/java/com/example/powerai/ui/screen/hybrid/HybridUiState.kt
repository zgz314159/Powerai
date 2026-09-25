package com.example.powerai.ui.screen.hybrid

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.domain.model.LocalAnswerFeedback
import com.example.powerai.domain.model.LocalSearchEntry

data class HybridUiState(
    val question: String = "",
    val sanitizedQuestion: String = "",
    val answer: String = "",
    val references: List<KnowledgeItem> = emptyList(),
    val isLoading: Boolean = false,
    val askedAtMillis: Long? = null,
    val importProgress: ImportProgress? = null,
    val webSearchEnabled: Boolean = false,

    val aiAnswer: String? = null,
    val localSummaryState: LocalSummaryState = LocalSummaryState(),
    val localAnswerFeedback: LocalAnswerFeedback = LocalAnswerFeedback.NONE,
    val localCurrentPage: Int = 1,
    val localCollapsedGroupKeys: Set<String> = emptySet(),
    val localScrollIndex: Int = 0,
    val localScrollOffset: Int = 0,
    val smartScrollIndex: Int = 0,
    val smartScrollOffset: Int = 0,
    val evidenceList: List<RetrievalResult> = emptyList(),
    val localSearchHistory: List<LocalSearchEntry> = emptyList(),
    val smartSearchHistory: List<LocalSearchEntry> = emptyList()
)
