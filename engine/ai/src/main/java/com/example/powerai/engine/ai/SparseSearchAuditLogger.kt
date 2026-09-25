package com.example.powerai.engine.ai

import android.content.Context
import java.io.File

/**
 * Helper for writing audit/diagnostic files used by [SparseSearcher].
 *
 * Previously the logging code was inlined in search(); moving it here keeps
 * the searcher focused on scoring and enables unit testing of logging
 * behaviour.  All methods tolerate a null context and ``no-op`` if
 * the directory is unavailable.
 */
object SparseSearchAuditLogger {
    private fun safeFile(context: Context?, name: String): File? {
        return try { context?.filesDir?.resolve(name) } catch (_: Throwable) { null }
    }

    fun writeSearchTriggered(context: Context?, query: String) {
        try {
            val f = safeFile(context, "sparse_hit_sentinel.txt") ?: return
            f.writeText("SEARCH_TRIGGERED_AT_${System.currentTimeMillis()}")
        } catch (_: Throwable) {
        }
    }

    fun writeLastSearch(context: Context?, query: String, tokens: List<String>) {
        try {
            val dbg = safeFile(context, "sparse_last_search.txt") ?: return
            val sb = StringBuilder()
            sb.append("query=$query\n")
            sb.append("tokens=${tokens.joinToString(",")}\n")
            dbg.writeText(sb.toString(), Charsets.UTF_8)
            // also create a minimal hits sentinel to prove the path executed
            try {
                val hitsSent = safeFile(context, "sparse_last_hits.txt")
                val line = "[sentinel] query=$query | ts=${System.currentTimeMillis()}\n"
                if (hitsSent != null) {
                    hitsSent.writeText(line, Charsets.UTF_8)
                }
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }

    fun writeHitDetails(
        context: Context?,
        scores: List<Triple<SparseSearcher.SafetyEntry, Int, Double>>
    ) {
        try {
            val hitsFile = safeFile(context, "sparse_last_hits.txt") ?: return
            if (hitsFile.exists()) hitsFile.delete()
            val sbh = StringBuilder()
            if (scores.isNotEmpty()) {
                var rank = 1
                for ((ent, raw, sc) in scores.sortedByDescending { it.third }) {
                    val weight = if (raw != 0) sc / raw.toDouble() else 0.0
                    sbh.append("[${rank}] ${ent.title} | ${ent.source} | Raw: ${raw} | Weight: ${"%.2f".format(weight)} | Final: ${"%.2f".format(sc)}\n")
                    rank += 1
                }
            }
            hitsFile.writeText(sbh.toString(), Charsets.UTF_8)
        } catch (_: Throwable) {
        }
    }
}
