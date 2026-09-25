package com.example.powerai.engine.ai

/**
 * Stateless helper that computes a simple weighted score for each safety entry
 * given a list of query tokens.  This replaces inlined logic previously in
 * [SparseSearcher] to make scoring easier to test and maintain.
 */
object SparseScorer {
    data class Score(val entry: SparseSearcher.SafetyEntry, val raw: Int, val final: Double)

    private fun manualWeightFor(sourceDisplay: String): Double {
        val s = sourceDisplay.trim()
        return when {
            s.contains("规程") || s.contains("安全工作规程") || s.contains("电力安全") -> 1.5
            s.contains("线路工") || s.contains("值班员") || s.contains("必知必会") -> 1.2
            else -> 1.0
        }
    }

    /**
     * Return detailed scores for each entry; useful for auditing/logging.
     * Entries with zero raw matches are omitted. The returned list is sorted
     * by final score descending.
     */
    fun scoreWithBreakdown(
        entries: List<SparseSearcher.SafetyEntry>,
        tokens: List<String>
    ): List<Score> {
        if (tokens.isEmpty() || entries.isEmpty()) return emptyList()

        val scores = mutableListOf<Score>()
        for (e in entries) {
            var rawScore = 0
            try {
                for (t in tokens) {
                    if (t.isEmpty()) continue
                    val esc = Regex.escape(t)
                    try {
                        val titleMatches = Regex(esc, RegexOption.IGNORE_CASE).findAll(e.title).count()
                        val contentMatches = Regex(esc, RegexOption.IGNORE_CASE).findAll(e.content).count()
                        rawScore += titleMatches * 5
                        rawScore += contentMatches * 1
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
            if (rawScore <= 0) continue
            val weight = try { manualWeightFor(e.source) } catch (_: Throwable) { 1.0 }
            val finalScore = rawScore.toDouble() * weight
            scores.add(Score(e, rawScore, finalScore))
        }

        return scores.sortedByDescending { it.final }
    }

    /**
     * Given a collection of entries and tokens (already produced by
     * [SparseTokenizer]), return the entries sorted by final weighted score
     * descending.  Entries with zero raw matches are omitted.
     *
     * This wrapper preserves the original public signature while delegating to
     * [scoreWithBreakdown].
     */
    fun score(entries: List<SparseSearcher.SafetyEntry>, tokens: List<String>): List<SparseSearcher.SafetyEntry> {
        return scoreWithBreakdown(entries, tokens).map { it.entry }
    }
}