package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.SearchEntry
import com.example.powerai.domain.repository.HistoryScope
import com.example.powerai.domain.repository.SearchHistoryRepository
import javax.inject.Inject

/**
 * 提供本地&智能搜索历史的状态管理与持久化
 *
 * 该用例保存了内存中的历史列表，并暴露[StateFlow] UI 层订阅
 * 所有变更函数都suspend，以便宿主（例如 ViewModel）在其作用域
 * 发起协程调用，避免在本类内部使用 GlobalScope
 */
class HybridHistoryUseCase @Inject constructor(
    private val historyRepository: SearchHistoryRepository
) {
    private val _localHistory = kotlinx.coroutines.flow.MutableStateFlow<List<SearchEntry>>(emptyList())
    val localHistory: kotlinx.coroutines.flow.StateFlow<List<SearchEntry>> = _localHistory

    private val _smartHistory = kotlinx.coroutines.flow.MutableStateFlow<List<SearchEntry>>(emptyList())
    val smartHistory: kotlinx.coroutines.flow.StateFlow<List<SearchEntry>> = _smartHistory

    /**
     * 从持久层加载已保存历史记录。应当在 ViewModel 初始化时调用一次
     */
    suspend fun init() {
        _localHistory.value = historyRepository.loadHistory(HistoryScope.LOCAL)
        _smartHistory.value = historyRepository.loadHistory(HistoryScope.SMART)
    }

    suspend fun addLocal(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        val now = System.currentTimeMillis()
        val deduped = _localHistory.value.filter { it.query != trimmed }
        val newList = listOf(SearchEntry(trimmed, now)) + deduped
        _localHistory.value = newList
        historyRepository.saveHistory(HistoryScope.LOCAL, newList)
    }

    suspend fun addSmart(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        val now = System.currentTimeMillis()
        val deduped = _smartHistory.value.filter { it.query != trimmed }
        val newList = listOf(SearchEntry(trimmed, now)) + deduped
        _smartHistory.value = newList
        historyRepository.saveHistory(HistoryScope.SMART, newList)
    }

    suspend fun clearLocal() {
        _localHistory.value = emptyList()
        historyRepository.clearHistory(HistoryScope.LOCAL)
    }

    suspend fun clearSmart() {
        _smartHistory.value = emptyList()
        historyRepository.clearHistory(HistoryScope.SMART)
    }
}
