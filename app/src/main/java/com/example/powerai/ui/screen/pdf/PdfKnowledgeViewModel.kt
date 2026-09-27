package com.example.powerai.ui.screen.pdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.domain.usecase.PdfKnowledgeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfKnowledgeViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
) : ViewModel() {

    private val useCase = PdfKnowledgeUseCase(repository)

    private val _knowledgeCount = MutableStateFlow(0)
    val knowledgeCount: StateFlow<Int> = _knowledgeCount

    private val _firstItemId = MutableStateFlow<Long?>(null)
    val firstItemId: StateFlow<Long?> = _firstItemId

    private val _pageItems = MutableStateFlow<List<com.example.powerai.core.model.KnowledgeItem>>(emptyList())
    val pageItems: StateFlow<List<com.example.powerai.core.model.KnowledgeItem>> = _pageItems

    private val _pageBlocks = MutableStateFlow<List<Pair<Long, com.example.powerai.core.model.KnowledgeBlock>>>(emptyList())
    val pageBlocks: StateFlow<List<Pair<Long, com.example.powerai.core.model.KnowledgeBlock>>> = _pageBlocks

    private var currentFileId: String = ""
    private var currentPage: Int = -1

    fun updatePage(fileId: String, page: Int) {
        if (fileId.isBlank()) return
        if (fileId == currentFileId && page == currentPage) return
        currentFileId = fileId
        currentPage = page

        viewModelScope.launch {
            val result = useCase.loadPage(fileId, page)
            _knowledgeCount.value = result.knowledgeCount
            _pageItems.value = result.pageItems
            _firstItemId.value = result.pageItems.firstOrNull()?.id
            _pageBlocks.value = result.pageBlocks
        }
    }
}
