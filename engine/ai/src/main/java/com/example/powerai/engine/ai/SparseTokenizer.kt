package com.example.powerai.engine.ai

/**
 * Helper responsible for breaking a user query into tokens suitable for the
 * simplistic sparse matching used by [SparseSearcher].
 *
 * This extraction allows the searcher to focus on scanning/lookup/score while
 * the token logic can be independently tested or replaced with a more advanced
 * strategy in the future.
 */
object SparseTokenizer {
    /**
     * Naive tokenization: split into consecutive Han-character runs and
     * alphanumeric runs; additionally generate short n-grams (2-3 chars) for
     * Han runs to improve recall.
     */
    fun tokenize(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val rawTokens = mutableListOf<String>()
        try {
            val tokenRegex = Regex("[\\p{IsHan}]+|[0-9A-Za-z]+")
            for (m in tokenRegex.findAll(query)) {
                val t = m.value.trim()
                if (t.isNotEmpty()) rawTokens.add(t)
            }
        } catch (_: Throwable) {}
        if (rawTokens.isEmpty()) return emptyList()

        val augmented = mutableSetOf<String>()
        for (t in rawTokens) {
            augmented.add(t)
            if (t.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) {
                val maxGram = 3
                val minGram = 2
                val len = t.length
                for (g in minGram..maxGram) {
                    if (len >= g) {
                        for (i in 0..(len - g)) {
                            augmented.add(t.substring(i, i + g))
                        }
                    }
                }
            }
        }
        return augmented.toList()
    }
}
