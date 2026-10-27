package com.example.powerai.ui.screen.hybrid

import com.example.powerai.core.data.importer.ImportProgress
import com.example.powerai.domain.model.SearchEntry
import com.example.powerai.domain.usecase.HybridHistoryUseCase
import com.example.powerai.feature.hybrid.HybridKbImportPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class HybridViewModelSideEffectCoordinator(
    private val historyUseCase: HybridHistoryUseCase,
    private val importer: HybridKbImportPort,
    private val ioDispatcher: CoroutineDispatcher,
) {
    val localSearchHistory: StateFlow<List<SearchEntry>> = historyUseCase.localHistory
    val smartSearchHistory: StateFlow<List<SearchEntry>> = historyUseCase.smartHistory

    fun observeImportProgress(
        scope: CoroutineScope,
        onProgress: (ImportProgress?) -> Unit,
    ): Job =
        scope.launch {
            importer.progress.collect { progress ->
                onProgress(progress)
            }
        }

    fun initialize(scope: CoroutineScope): Job =
        scope.launch(ioDispatcher) {
            try {
                historyUseCase.init()
            } catch (_: Throwable) {
            }

            try {
                importer.ensureAssetsImported()
            } catch (_: Throwable) {
            }
        }

    fun addLocalQuery(
        scope: CoroutineScope,
        query: String,
    ): Job =
        scope.launch(ioDispatcher) {
            historyUseCase.addLocal(query)
        }

    fun addSmartQuery(
        scope: CoroutineScope,
        query: String,
    ): Job =
        scope.launch(ioDispatcher) {
            historyUseCase.addSmart(query)
        }

    fun clearLocalHistory(scope: CoroutineScope): Job =
        scope.launch(ioDispatcher) {
            historyUseCase.clearLocal()
        }

    fun clearSmartHistory(scope: CoroutineScope): Job =
        scope.launch(ioDispatcher) {
            historyUseCase.clearSmart()
        }
}
