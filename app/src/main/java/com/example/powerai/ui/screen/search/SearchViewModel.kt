package com.example.powerai.ui.screen.search

import com.example.powerai.core.model.KnowledgeItem

import androidx.lifecycle.viewModelScope
import com.example.powerai.feature.searchchat.RetrievalFusionUseCase
import com.example.powerai.ui.mvi.BaseMviViewModel
import com.example.powerai.ui.screen.hybrid.HybridUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val sanitizedQuery: String = "",
    val isLoading: Boolean = false,
    val results: List<KnowledgeItem> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val localSearchUseCase: RetrievalFusionUseCase
) : BaseMviViewModel<SearchIntent, SearchUiState, Nothing>(
    initialState = SearchUiState()
) {

    private var searchJob: kotlinx.coroutines.Job? = null

    override fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> updateState { copy(query = intent.query) }
            is SearchIntent.Search -> onSearch()
        }
    }

    private fun onSearch() {
        val keyword = currentState.query
        val sanitized = HybridUtils.sanitizeQuestion(keyword)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                updateState { copy(isLoading = true, error = null, sanitizedQuery = sanitized) }
                val results = localSearchUseCase.invoke(sanitized)
                updateState { copy(isLoading = false, results = results.toList()) }
            } catch (t: Throwable) {
                updateState { copy(isLoading = false, error = t.message) }
            }
        }
    }
}
