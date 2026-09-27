package com.example.powerai.ui.screen.database

import com.example.powerai.domain.model.SearchEntry
import com.example.powerai.domain.usecase.DatabaseUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Search history store for [DatabaseViewModel]: initial read, deduplicated
 * newest-first updates, persistence and clearing. Reuses [DatabaseUseCase]
 * for the persisted history so there is only one history abstraction.
 */
internal class DatabaseSearchHistoryStore(
    private val useCase: DatabaseUseCase,
    private val ioDispatcher: CoroutineDispatcher,
    private val state: () -> DatabaseUiState,
    private val reduce: (DatabaseUiState.() -> DatabaseUiState) -> Unit,
) {
    private companion object {
        private const val MAX_ENTRIES = 50
    }

    /** Loads persisted history into state; failures keep the empty default. */
    suspend fun initialize() {
        try {
            val loaded = useCase.loadHistory()
            if (loaded.isNotEmpty()) reduce { copy(searchHistory = loaded) }
        } catch (_: Throwable) {
        }
    }

    fun add(
        scope: CoroutineScope,
        rawQuery: String,
    ) {
        if (rawQuery.isBlank()) return
        val query = rawQuery.trim()
        val now = System.currentTimeMillis()
        val deduped = state().searchHistory.filter { it.query != query }
        val newList = listOf(SearchEntry(query, now)) + deduped
        reduce {
            copy(searchHistory = if (newList.size > MAX_ENTRIES) newList.take(MAX_ENTRIES) else newList)
        }
        scope.launch(ioDispatcher) {
            useCase.addSearchHistory(query)
        }
    }

    fun clear(scope: CoroutineScope) {
        reduce { copy(searchHistory = emptyList()) }
        scope.launch(ioDispatcher) {
            useCase.clearSearchHistory()
        }
    }
}
