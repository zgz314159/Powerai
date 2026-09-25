package com.example.powerai.data.repository

import com.example.powerai.data.local.DatabaseSearchHistoryStore
import com.example.powerai.data.local.LocalSearchHistoryStore
import com.example.powerai.data.local.SmartSearchHistoryStore
import com.example.powerai.domain.model.SearchEntry
import com.example.powerai.domain.repository.HistoryScope
import com.example.powerai.domain.repository.SearchHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepositoryImpl @Inject constructor(
    private val dbStore: DatabaseSearchHistoryStore,
    private val localStore: LocalSearchHistoryStore,
    private val smartStore: SmartSearchHistoryStore
) : SearchHistoryRepository {

    override suspend fun loadHistory(scope: HistoryScope): List<SearchEntry> {
        val raw = when (scope) {
            HistoryScope.DATABASE -> dbStore.load()
            HistoryScope.LOCAL -> localStore.load()
            HistoryScope.SMART -> smartStore.load()
        }
        return raw.map { SearchEntry(it.query, it.timestamp) }
    }

    override suspend fun saveHistory(scope: HistoryScope, history: List<SearchEntry>) {
        val data = history.map { com.example.powerai.data.local.LocalSearchEntry(it.query, it.timestamp) }
        when (scope) {
            HistoryScope.DATABASE -> dbStore.save(data)
            HistoryScope.LOCAL -> localStore.save(data)
            HistoryScope.SMART -> smartStore.save(data)
        }
    }

    override suspend fun clearHistory(scope: HistoryScope) {
        when (scope) {
            HistoryScope.DATABASE -> dbStore.clear()
            HistoryScope.LOCAL -> localStore.clear()
            HistoryScope.SMART -> smartStore.clear()
        }
    }
}
