package com.example.powerai.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.usecase.LocalSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI 状态容器，供 `SearchScreen` 订阅。
 */
data class SearchUiState(
	val query: String = "",
	val isLoading: Boolean = false,
	val results: List<KnowledgeItem> = emptyList(),
	val error: String? = null
)

/**
 * 仅负责接收用户输入并调用 UseCase 执行本地搜索，不包含任何 UI 相关实现。
 */
@HiltViewModel
class SearchViewModel @Inject constructor(private val localSearchUseCase: LocalSearchUseCase) : ViewModel() {
	private val _uiState = MutableStateFlow(SearchUiState())
	val uiState: StateFlow<SearchUiState> = _uiState

	fun onQueryChanged(q: String) {
		_uiState.value = _uiState.value.copy(query = q)
	}

	fun onSearch() {
		val keyword = _uiState.value.query
		viewModelScope.launch {
			_uiState.value = _uiState.value.copy(isLoading = true, error = null)
			try {
				val results = localSearchUseCase.invoke(keyword)
				_uiState.value = _uiState.value.copy(isLoading = false, results = results)
			} catch (t: Throwable) {
				_uiState.value = _uiState.value.copy(isLoading = false, error = t.message)
			}
		}
	}
}

