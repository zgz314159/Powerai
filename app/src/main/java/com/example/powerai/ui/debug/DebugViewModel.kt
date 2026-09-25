package com.example.powerai.ui.debug

import com.example.powerai.core.data.dao.KnowledgeDao

import androidx.lifecycle.viewModelScope
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebugUiState(
    val items: List<KnowledgeEntity> = emptyList()
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val dao: KnowledgeDao
) : BaseMviViewModel<DebugIntent, DebugUiState, Nothing>(
    initialState = DebugUiState()
) {

    override fun onIntent(intent: DebugIntent) {
        when (intent) {
            is DebugIntent.ReloadAll -> reloadAll()
            is DebugIntent.UpdateEntry -> updateEntry(intent.entity)
        }
    }

    private fun reloadAll() {
        viewModelScope.launch {
            val items = dao.getAll()
            updateState { copy(items = items) }
        }
    }

    private fun updateEntry(entity: KnowledgeEntity) {
        viewModelScope.launch {
            dao.update(entity)
            reloadAll()
        }
    }
}
