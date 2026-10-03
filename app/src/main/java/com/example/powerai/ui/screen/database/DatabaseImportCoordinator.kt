package com.example.powerai.ui.screen.database

import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.util.PLog
import kotlinx.coroutines.CancellationException

/**
 * Import maintenance for the database tab: a real retry of failed/missing built-in assets and a
 * user-confirmed rebuild of the built-in KB. The retry re-runs the production asset import (which
 * skips unchanged content and re-imports failed/missing packages) instead of only refreshing the
 * diagnostics view. The rebuild only runs after the user confirms, and its lifecycle is surfaced
 * through [DocumentImportManager.rebuildState].
 */
internal class DatabaseImportCoordinator(
    private val importManager: DocumentImportManager,
    private val reduce: (DatabaseUiState.() -> DatabaseUiState) -> Unit,
    private val reload: () -> Unit,
) {
    private companion object {
        private const val TAG = "PowerAiDbImport"
    }

    /** Retry failed/missing assets, refresh diagnostics, then reload the list. */
    suspend fun retryImport() {
        runSafely("retry asset import") { importManager.importAssetsIfNeed() }
        refreshDiagnostics()
    }

    /** Show the rebuild confirmation; nothing is deleted until the user confirms. */
    fun requestRebuild() {
        reduce { copy(isRebuildConfirmVisible = true) }
    }

    fun cancelRebuild() {
        reduce { copy(isRebuildConfirmVisible = false) }
    }

    /** Run the confirmed rebuild, then refresh diagnostics and reload the list. */
    suspend fun confirmRebuild() {
        reduce { copy(isRebuildConfirmVisible = false) }
        runSafely("rebuild built-in KB") { importManager.rebuildBuiltInKnowledgeBase() }
        refreshDiagnostics()
    }

    private suspend fun refreshDiagnostics() {
        runSafely("refresh import diagnostics") { importManager.refreshImportDiagnostics() }
        reload()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runSafely(
        label: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            PLog.w(TAG, "$label failed: ${t.message}")
        }
    }
}
