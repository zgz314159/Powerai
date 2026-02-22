package com.example.powerai.data.repository

internal object KnowledgeSearchSqlPatterns {

    fun buildFtsMatchQuery(normalizedQuery: String): String {
        val q = normalizedQuery.trim().replace(Regex("\\s+"), " ")
        if (q.isBlank()) return ""

        // Tokenize by spaces (sanitizer already removed punctuation/symbols).
        val tokens = q.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""

        // For multi-token queries: require all tokens, allow prefix match on each.
        // Example: "铁路 电力" -> "铁路* AND 电力*"
        if (tokens.size > 1) {
            return tokens.joinToString(" AND ") { token -> "$token*" }
        }

        // Single-token: allow prefix OR exact.
        val t = tokens.first()
        return "$t* OR $t"
    }

    fun buildFuzzyLikePattern(qNoSpace: String): String? {
        val s = qNoSpace.trim()
        if (s.length !in 3..12) return null
        val escaped = buildString(s.length) {
            for (c in s) {
                when (c) {
                    '%', '_', '/' -> {
                        append('/')
                        append(c)
                    }

                    else -> append(c)
                }
            }
        }
        return "%" + escaped.toCharArray().joinToString("%") + "%"
    }
}
