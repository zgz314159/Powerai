package com.example.powerai.ui.screen.pdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.domain.model.PdfFigureItem
import com.example.powerai.domain.usecase.PdfFigureListUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PdfFigureListViewModel @Inject constructor(
    private val dao: KnowledgeDao,
) : ViewModel() {

    private val useCase = PdfFigureListUseCase(dao)

    private val _figures = MutableStateFlow<List<PdfFigureItem>>(emptyList())
    val figures: StateFlow<List<PdfFigureItem>> = _figures

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var loadedForFileId: String? = null

    fun loadFor(fileId: String) {
        if (fileId.isBlank() || loadedForFileId == fileId) return
        loadedForFileId = fileId
        viewModelScope.launch {
            _isLoading.value = true
            val items = withContext(Dispatchers.IO) {
                useCase.loadFigures(fileId)
            }
            _figures.value = items
            _isLoading.value = false
        }
    }
}
