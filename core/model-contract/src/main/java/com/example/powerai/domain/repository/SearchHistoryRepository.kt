package com.example.powerai.domain.repository

import com.example.powerai.domain.model.SearchEntry

enum class HistoryScope { DATABASE, LOCAL, SMART }

interface SearchHistoryRepository {
    suspend fun loadHistory(scope: HistoryScope): List<SearchEntry>
    suspend fun saveHistory(scope: HistoryScope, history: List<SearchEntry>)
    suspend fun clearHistory(scope: HistoryScope)
}
