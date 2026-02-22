package com.example.powerai.data.repository

internal object KnowledgeSnippetBuilder {
    fun snippetAroundQuery(text: String, rawQuery: String): String {
        // Keep snippets short to avoid Compose/Text layout getting stuck on huge content.
        val maxTotal = 300
        val before = 100
        val after = 100

        if (text.length <= maxTotal) return text
        val q = rawQuery.trim()
        if (q.isBlank()) return text.take(maxTotal) + "…"

        fun findFirstFuzzyMatchStart(hay: String, needleNoWs: String): Int {
            if (needleNoWs.isBlank()) return -1
            var i = 0
            while (i < hay.length) {
                var t = i
                var j = 0
                while (t < hay.length && j < needleNoWs.length) {
                    val hc = hay[t]
                    if (hc.isWhitespace()) {
                        t++
                        continue
                    }
                    val nc = needleNoWs[j]
                    if (!hc.equals(nc, ignoreCase = true)) break
                    t++
                    j++
                }
                if (j == needleNoWs.length) return i
                i++
            }
            return -1
        }

        // 1) direct match (ignore case)
        var center = text.indexOf(q, ignoreCase = true)

        // 2) whitespace-insensitive fuzzy match (no heavy allocations)
        if (center < 0) {
            val qNoWs = q.filterNot { it.isWhitespace() }
            if (qNoWs.isNotBlank()) {
                center = findFirstFuzzyMatchStart(text, qNoWs)
            }
        }

        // 3) punctuation/whitespace-insensitive match based on the same "letters/digits only" rule
        // used by `TextSanitizer.normalizeForSearch` / `contentNormalized`.
        if (center < 0) {
            val qCompact = buildString(q.length) {
                for (ch in q) if (Character.isLetterOrDigit(ch)) append(ch.lowercaseChar())
            }
            if (qCompact.isNotBlank()) {
                val contentCompact = StringBuilder(text.length)
                val indexMap = ArrayList<Int>(text.length)
                for (i in text.indices) {
                    val ch = text[i]
                    if (Character.isLetterOrDigit(ch)) {
                        contentCompact.append(ch.lowercaseChar())
                        indexMap.add(i)
                    }
                }
                val pos = contentCompact.indexOf(qCompact)
                if (pos >= 0) {
                    center = indexMap.getOrNull(pos) ?: -1
                }
            }
        }

        if (center < 0) center = 0
        val start = kotlin.math.max(0, center - before)
        val end = kotlin.math.min(text.length, center + after)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        val snippet = prefix + text.substring(start, end) + suffix
        return if (snippet.length <= maxTotal) snippet else (snippet.take(maxTotal) + "…")
    }
}
