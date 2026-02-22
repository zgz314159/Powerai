package com.example.powerai.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.entity.KnowledgeEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(private val dao: KnowledgeDao) : ViewModel() {
    private val _items = MutableStateFlow<List<KnowledgeEntity>>(emptyList())
    val items: StateFlow<List<KnowledgeEntity>> = _items

    fun reloadAll() {
        viewModelScope.launch {
            _items.value = dao.getAll()
        }
    }

    fun updateEntry(entity: KnowledgeEntity) {
        viewModelScope.launch {
            dao.update(entity)
            reloadAll()
        }
    }
}
