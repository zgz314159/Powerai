package com.example.powerai.ui.screen.hybrid

import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.importer.ImportProgress
import com.example.powerai.domain.model.LocalSearchEntry
import com.example.powerai.domain.usecase.HybridHistoryUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class HybridViewModelSideEffectCoordinator(
    private val historyUseCase: HybridHistoryUseCase,
    private val importer: DocumentImportManager,
    private val ioDispatcher: CoroutineDispatcher
) {
    val localSearchHistory: StateFlow<List<LocalSearchEntry>> = historyUseCase.localHistory
    val smartSearchHistory: StateFlow<List<LocalSearchEntry>> = historyUseCase.smartHistory

    fun observeImportProgress(
        scope: CoroutineScope,
        onProgress: (ImportProgress?) -> Unit
    ): Job = scope.launch {
        importer.progress.collect { progress ->
            onProgress(progress)
        }
    }

    fun initialize(scope: CoroutineScope): Job = scope.launch(ioDispatcher) {
        try {
            historyUseCase.init()
        } catch (_: Throwable) {
        }

        try {
            importer.importAssetsIfNeed()
        } catch (_: Throwable) {
        }
    }

    fun addLocalQuery(scope: CoroutineScope, query: String): Job = scope.launch(ioDispatcher) {
        historyUseCase.addLocal(query)
    }

    fun addSmartQuery(scope: CoroutineScope, query: String): Job = scope.launch(ioDispatcher) {
        historyUseCase.addSmart(query)
    }

    fun clearLocalHistory(scope: CoroutineScope): Job = scope.launch(ioDispatcher) {
        historyUseCase.clearLocal()
    }

    fun clearSmartHistory(scope: CoroutineScope): Job = scope.launch(ioDispatcher) {
        historyUseCase.clearSmart()
    }
}
