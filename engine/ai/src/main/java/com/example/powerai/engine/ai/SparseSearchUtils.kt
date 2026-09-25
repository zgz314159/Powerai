package com.example.powerai.engine.ai

/**
 * Utility helpers factored out of [SparseSearcher.search] to keep the main
 * search method focused on orchestration.  These functions are exercised by
 * dedicated unit tests.
 */
object SparseSearchUtils {
    /**
     * Apply the "tie-break" rule used when the top two scores are very close.
     * If the difference between the first and second `final` score is less than
     * 1.0, we sort by manual-weight (final/raw ratio) then by final score.
     * Otherwise the original order is returned.
     */
    fun applyTieBreak(breakdown: List<SparseScorer.Score>): List<SparseSearcher.SafetyEntry> {
        if (breakdown.size < 2) return breakdown.map { it.entry }
        val top1 = breakdown[0]
        val top2 = breakdown[1]
        val diff = kotlin.math.abs(top1.final - top2.final)
        return if (diff < 1.0) {
            breakdown.sortedWith(
                compareByDescending<SparseScorer.Score> { if (it.raw != 0) it.final / it.raw else 0.0 }
                    .thenByDescending { it.final }
            ).map { it.entry }
        } else {
            breakdown.map { it.entry }
        }
    }

    /**
     * Fallback matching logic executed when scoring produced no results.  A
     * permissive contains-check is performed using tokens of length >= 2 and
     * a case-insensitive substring test of title/content.  Matching entries are
     * returned in the order encountered.
     */
    fun permissiveFallback(
        entries: List<SparseSearcher.SafetyEntry>,
        tokens: List<String>,
        query: String
    ): List<SparseSearcher.SafetyEntry> {
        if (entries.isEmpty() || tokens.isEmpty()) return emptyList()
        val fallbackHits = mutableListOf<SparseSearcher.SafetyEntry>()
        for (e in entries) {
            var matched = false
            if (!e.title.isNullOrBlank() && query.contains(e.title, ignoreCase = true)) matched = true
            if (!matched) {
                for (t in tokens) {
                    if (t.length >= 2 && (e.title.contains(t, ignoreCase = true) || e.content.contains(t, ignoreCase = true))) {
                        matched = true
                        break
                    }
                }
            }
            if (matched) fallbackHits.add(e)
        }
        return fallbackHits
    }
}
