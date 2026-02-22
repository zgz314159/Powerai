package com.example.powerai.data.importer

import com.google.gson.Gson

/**
 * Produces structured blocks JSON from raw imported content.
 *
 * Goal: after importing any source (pdf/docx/txt/json), the app can render/search from
 * preprocessed DB fields (`contentBlocksJson` + `contentNormalized`) without re-parsing.
 */
object BlocksPreprocessor {
    private val gson = Gson()

    fun blocksJsonFromPlainText(text: String): String? {
        val clean = text.trim()
        if (clean.isBlank()) return null

        // Split into paragraphs to improve hit navigation granularity.
        val parts = clean
            .split(Regex("\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val blocks: List<Map<String, Any>> = if (parts.isEmpty()) {
            listOf(mapOf("type" to "text", "text" to clean))
        } else {
            parts.map { p -> mapOf("type" to "text", "text" to p) }
        }

        return gson.toJson(blocks)
    }

    fun normalizedForSearchFromBlocksJson(blocksJson: String): String {
        val plain = BlocksTextExtractor.extractPlainText(blocksJson)
        val normalized = TextSanitizer.normalizeForSearch(plain)
        return normalized
    }
}
