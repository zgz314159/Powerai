package com.example.powerai.engine.ai


import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * SparseSearcher: reads a JSON knowledge base from app assets or files and
 * performs simple token/contains matching to return the best-matching rule.
 *
 * If `context` is null or the JSON cannot be read, falls back to a tiny
 * built-in map to preserve prior behavior.
 */
class SparseSearcher(
    private val context: Context? = null,
    private val loader: SparseSearcherLoader = DefaultSparseSearcherLoader()
) {
    data class SafetyEntry(val title: String, val content: String, val source: String)

    // Simple filename -> human-friendly handbook title mapping. Add more mappings as needed.
    private fun mapFilenameToTitle(filename: String): String {
        val fname = filename.lowercase()
        val known = mapOf(
            "power_safety_rules.json" to "电力安全工作规程",
            "railway_power_lines.json" to "电力线路工必知必会手册",
            "铁路电力线路工.json" to "电力线路工必知必会手册"
        )
        for ((k, v) in known) {
            if (fname.contains(k)) return v
        }
        // pattern matching
        if (fname.contains("rail") || fname.contains("铁路") || fname.contains("线路")) return "电力线路工必知必会手册"
        if (fname.contains("power") || fname.contains("safety") || fname.contains("安全")) return "电力安全工作规程"
        // fallback: strip extension and use as-is
        return filename
    }

    private val entries: List<SafetyEntry>

    init {
        entries = loader.loadEntries(context)
        // diagnostic manifest
        try {
            val f = context?.filesDir?.resolve("sparse_entries_loaded.txt")
            if (f != null) {
                val sb = StringBuilder()
                sb.append("entries=${entries.size}\n")
                for (e in entries) {
                    sb.append("- ${e.title} | src=${e.source}\n")
                }
                f.writeText(sb.toString(), Charsets.UTF_8)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Search for top N entries by simple scoring: sum of token frequencies in title/content.
     * Title matches are weighted by 5, content matches by 1.
     */
    fun search(query: String, topN: Int = 2): List<SafetyEntry> {
        try {
            // preliminary audit operations
            val q = query.ifBlank { "" }
            if (q.isBlank()) return emptyList()

            SparseSearchAuditLogger.writeSearchTriggered(context, q)

            // tokenize using the extracted helper (improves testability)
            val tokens = SparseTokenizer.tokenize(q)
            if (tokens.isEmpty()) return emptyList()

            SparseSearchAuditLogger.writeLastSearch(context, q, tokens)

            // use scorer to obtain breakdown (entry + raw + final)
            val breakdown = SparseScorer.scoreWithBreakdown(entries, tokens)
            var sorted = SparseSearchUtils.applyTieBreak(breakdown)

            // write detailed hit information for audit (convert to Triple for compatibility)
            val scoreTriples = breakdown.map { Triple(it.entry, it.raw, it.final) }
            SparseSearchAuditLogger.writeHitDetails(context, scoreTriples)
            if (sorted.isEmpty()) {
                // Fallback: permissive contains-based match using tokens
                sorted = SparseSearchUtils.permissiveFallback(entries, tokens, q)
                // If we used permissive fallback, log those hits with score 0 for audit
                // fallback hit audit write removed
            }
            // If we had scored hits earlier, but we are returning a truncated topN, ensure the audit file reflects returned hits
            // final returned-hits audit write removed

            return if (sorted.isEmpty()) emptyList() else sorted.take(topN)
        } catch (_: Throwable) {}
        return emptyList()
    }
}