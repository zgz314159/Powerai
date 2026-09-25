package com.example.powerai.core.data.repository

import com.example.powerai.core.data.BuildConfig
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.database.DatabaseMigrationUtils

/**
 * Encapsulates one-time diagnostics and self-heal logic used by
 * [KnowledgeLocalSearch]. Extracting this state makes the main search path
 * easier to read and also allows unit testing of the heuristics in isolation.
 */
object LocalSearchDiagnostics {
    @Volatile
    private var didOneTimeDiagnostics: Boolean = false

    @Volatile
    private var lastSnippetDiagnosticsQuery: String? = null

    @Volatile
    private var didOneTimeNormalizeSelfHeal: Boolean = false

    /**
     * Run the one-time normalized-data self-heal which fixes entries missing
     * a normalized/search field. Safe to call repeatedly; it only executes
     * once per process in DEBUG builds.
     */
    internal suspend fun ensureSelfHeal(dao: KnowledgeDao) {
        if (!didOneTimeNormalizeSelfHeal) {
            didOneTimeNormalizeSelfHeal = true
            try {
                if (BuildConfig.DEBUG) {
                    // log removed: searchLocal trigger one-time normalized self-heal
                }
                DatabaseMigrationUtils.fillMissingNormalizedData(dao)
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    /**
     * Determine whether we should emit extra snippet diagnostics for this query.
     * The logic mirrors the original block in KnowledgeLocalSearch.searchLocal.
     * If diagnostics are enabled the internal state is updated to prevent
     * re-emitting the same query repeatedly.
     */
    fun shouldEmitSnippetDiagnostics(qNormalized: String, hasCjk: Boolean): Boolean {
        val enable = BuildConfig.DEBUG &&
            qNormalized.isNotBlank() &&
            qNormalized.length <= 24 &&
            (hasCjk || qNormalized.any { it.isLetterOrDigit() }) &&
            (lastSnippetDiagnosticsQuery != qNormalized)
        if (enable) {
            lastSnippetDiagnosticsQuery = qNormalized
            // log removed: KB_SNIPPET_DIAG start query info
        }
        return enable
    }

    /**
     * Log an initial FTS row count once in DEBUG mode. This replicates the earlier
     * `didOneTimeDiagnostics` check inside KnowledgeLocalSearch.
     */
    suspend fun logInitialFtsCount(dao: KnowledgeDao) {
        if (BuildConfig.DEBUG && !didOneTimeDiagnostics) {
            didOneTimeDiagnostics = true
            try {
                val ftsRows = dao.countFts()
                // log removed: SEARCH_CHECK ftsRowCount
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Reset internal diagnostic flags. Intended for use in unit tests only.
     */
    fun resetForTests() {
        didOneTimeDiagnostics = false
        lastSnippetDiagnosticsQuery = null
        didOneTimeNormalizeSelfHeal = false
    }
}
