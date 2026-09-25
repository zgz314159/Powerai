package com.example.powerai.ui.screen.pdf

import com.example.powerai.core.model.KnowledgeBlock
import com.example.powerai.core.repository.KnowledgeRepository

import com.example.powerai.core.model.KnowledgeItem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfKnowledgeViewModel @Inject constructor(
    private val repository: KnowledgeRepository
) : ViewModel() {

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
            val count = repository.countKnowledgeByPage(fileId, page)
            _knowledgeCount.value = count
            if (count > 0) {
                val items = repository.getItemsByPage(fileId, page)
                _pageItems.value = items
                _firstItemId.value = items.firstOrNull()?.id

                // Extract all blocks for hotspot display
                val allBlocks = items.flatMap { item ->
                    com.example.powerai.ui.blocks.BlocksParser.parseBlocks(item.contentBlocksJson).orEmpty().map { item.id to it }
                }
                _pageBlocks.value = allBlocks
            } else {
                _pageItems.value = emptyList()
                _firstItemId.value = null
                _pageBlocks.value = emptyList()
            }
        }
    }
}
