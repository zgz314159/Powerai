package com.example.powerai.data.importer

/** Per-asset import status values shared by the importer, diagnostics and UI. */
object AssetImportStatus {
    const val MISSING = "missing"
    const val IMPORTED = "imported"
    const val IN_PROGRESS = "in_progress"
    const val FAILED = "failed"
    const val LEGACY = "legacy"
}

data class AssetImportDiagnosticEntry(
    val assetPath: String,
    val fileId: String,
    val displayName: String,
    val status: String,
    val rowCount: Int,
    val errorMessage: String? = null,
)

data class AssetImportDiagnostics(
    val assetRoot: String = "kb",
    val scannedCount: Int = 0,
    val importedCount: Int = 0,
    val failedCount: Int = 0,
    val zeroRowCount: Int = 0,
    val entries: List<AssetImportDiagnosticEntry> = emptyList(),
    val lastScanAt: Long = 0L,
)

/**
 * Aggregate result of one built-in asset import pass. Unlike [AssetImportDiagnostics] this is
 * returned to the caller (e.g. the preload worker) so a per-asset failure is never mistaken for
 * overall success.
 */
data class AssetImportOutcome(
    val scanned: Int = 0,
    val imported: Int = 0,
    val failed: Int = 0,
    val legacy: Int = 0,
) {
    val hasFailures: Boolean get() = failed > 0
}

/**
 * Honest lifecycle of a user-triggered "rebuild built-in knowledge base": running, success,
 * failure and cancellation are distinguishable, and a failure never looks like an empty success.
 */
sealed interface KbRebuildState {
    data object Idle : KbRebuildState

    data class Running(
        val processed: Int,
        val total: Int,
        val currentName: String?,
    ) : KbRebuildState

    data class Success(
        val knowledgeRows: Int,
        val packages: Int,
    ) : KbRebuildState

    data class Failed(
        val reason: String,
    ) : KbRebuildState

    data object Cancelled : KbRebuildState
}
