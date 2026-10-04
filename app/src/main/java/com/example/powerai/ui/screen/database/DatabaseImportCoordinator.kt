package com.example.powerai.ui.screen.database

import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.util.PLog
import kotlinx.coroutines.CancellationException

/**
 * Import maintenance for the database tab: a real retry of failed/missing built-in assets, a
 * user-confirmed rebuild of the confirmable built-in packages only, and an explicit two-stage
 * "clear all knowledge" escape hatch. Nothing is deleted before the user confirms, and each
 * lifecycle is surfaced through the manager's state flows. User-package lifecycle lives in
 * [DatabaseUserPackageCoordinator].
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

    /** Rebuild only the confirmable built-in packages, then refresh diagnostics and reload. */
    suspend fun confirmRebuild() {
        reduce { copy(isRebuildConfirmVisible = false) }
        runSafely("rebuild built-in KB") { importManager.rebuildBuiltInKnowledgeBase() }
        refreshDiagnostics()
    }

    fun requestClearAll() {
        reduce { copy(clearAllStep = ClearAllConfirmStep.SCOPE) }
    }

    fun continueClearAll() {
        reduce { copy(clearAllStep = ClearAllConfirmStep.FINAL) }
    }

    fun cancelClearAll() {
        reduce { copy(clearAllStep = ClearAllConfirmStep.NONE) }
    }

    /** Execute the confirmed "clear all": wipe every package, then refresh and reload. */
    suspend fun confirmClearAll() {
        reduce { copy(clearAllStep = ClearAllConfirmStep.NONE) }
        runSafely("clear all knowledge") { importManager.clearAllKnowledgeBases() }
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
