package com.example.powerai.data.importer

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest

/**
 * Minimal blocks->plainText extractor.
 *
 * Design goal:
 * - Generate a stable, searchable text for `KnowledgeEntity.contentNormalized`.
 * - Be schema-tolerant: unknown block shapes should still contribute useful text.
 */
object BlocksTextExtractor {

    private val META_KEYS_TO_SKIP = setOf(
        "id",
        "blockid",
        "type",
        "blocktype",
        "language",
        "pagenumber",
        "position",
        "kind"
    )

    private fun parseRoot(blocksJson: String): JsonElement? {
        return try {
            JsonParser().parse(blocksJson)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractBlocksArray(root: JsonElement?): JsonArray? {
        if (root == null || root.isJsonNull) return null
        if (root.isJsonArray) return root.asJsonArray
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        val keys = listOf("blocks", "contentBlocks", "content_blocks")
        for (k in keys) {
            val v = obj.get(k)
            if (v != null && v.isJsonArray) return v.asJsonArray
        }
        return null
    }

    private fun extractPlainTextFromElement(el: JsonElement?): String {
        if (el == null || el.isJsonNull) return ""
        val out = StringBuilder(256)

        fun appendToken(s: String) {
            val t = s.trim()
            if (t.isBlank()) return
            if (out.isNotEmpty()) out.append(' ')
            out.append(t)
        }

        fun visit(node: JsonElement?) {
            if (node == null || node.isJsonNull) return
            when {
                node.isJsonPrimitive -> {
                    if (node.asJsonPrimitive.isString) appendToken(node.asString)
                }
                node.isJsonArray -> {
                    for (child in node.asJsonArray) visit(child)
                }
                node.isJsonObject -> {
                    val obj = node.asJsonObject

                    // Prefer content-like keys and avoid pulling block meta into snippets.
                    // Many preprocessed KBs store markdown under `code` for code blocks.
                    obj.get("code")?.let { codeEl ->
                        if (codeEl.isJsonPrimitive && codeEl.asJsonPrimitive.isString) {
                            appendToken(codeEl.asString)
                            return
                        }
                    }

                    // Prefer common text keys.
                    listOf("text", "title", "content", "caption", "alt").forEach { key ->
                        val v = obj.get(key)
                        if (v != null && v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                            appendToken(v.asString)
                        }
                    }
                    // Table-like cell arrays.
                    obj.get("cells")?.let { visit(it) }
                    // TableBlock schema uses `rows`.
                    obj.get("rows")?.let { visit(it) }
                    // Rich-text spans.
                    obj.get("spans")?.let { visit(it) }
                    // Generic recursion.
                    for ((k, v) in obj.entrySet()) {
                        val key = k.trim().lowercase()
                        if (key in META_KEYS_TO_SKIP) continue
                        if (key == "code") continue
                        visit(v)
                    }
                }
            }
        }

        visit(el)
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun sha1Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private fun extractTypeFromElement(el: JsonElement?): String {
        if (el == null || el.isJsonNull) return ""
        if (!el.isJsonObject) return ""
        val obj = el.asJsonObject
        val typeEl = obj.get("type") ?: obj.get("blockType")
        if (typeEl == null || !typeEl.isJsonPrimitive) return ""
        val prim = typeEl.asJsonPrimitive
        if (!prim.isString) return ""
        return prim.asString.trim().lowercase()
    }

    /**
     * Compute stable IDs for each top-level block element.
     *
     * - If the JSON already provides id/blockId, keep it.
     * - Otherwise generate a deterministic id from (type + normalized text) and disambiguate duplicates.
     */
    fun computeStableBlockIds(blocksJson: String): List<String> {
        val root = parseRoot(blocksJson) ?: return emptyList()
        val blocks = extractBlocksArray(root) ?: return emptyList()
        val ids = ArrayList<String>(blocks.size())
        val seen = HashMap<String, Int>(blocks.size())

        for (el in blocks) {
            val existing = if (el.isJsonObject) {
                val obj = el.asJsonObject
                val idEl = obj.get("id") ?: obj.get("blockId")
                if (idEl != null && idEl.isJsonPrimitive && idEl.asJsonPrimitive.isString) idEl.asString.trim() else null
            } else {
                null
            }

            if (!existing.isNullOrBlank()) {
                ids.add(existing)
                continue
            }

            val type = extractTypeFromElement(el)
            val text = extractPlainTextFromElement(el)
            val norm = ("$type|$text").lowercase().replace(Regex("\\s+"), " ").trim()
            val baseHash = sha1Hex(norm).take(12)
            val n = (seen[baseHash] ?: 0) + 1
            seen[baseHash] = n
            ids.add("b_${baseHash}_$n")
        }
        return ids
    }

    /**
     * Extract per-block plain text (top-level block array order).
     * Used for block-level hit locating.
     */
    fun extractBlockPlainTexts(blocksJson: String): List<String> {
        val root = parseRoot(blocksJson) ?: return emptyList()
        val blocks = extractBlocksArray(root)
        if (blocks != null) {
            // IMPORTANT: keep index alignment with the original blocks array.
            // Do NOT filter blank blocks here, otherwise hit indices will drift and stop matching stable IDs.
            return blocks.map { extractPlainTextFromElement(it) }
        }
        // Fallback: treat the whole object as a single block.
        val single = extractPlainTextFromElement(root)
        return if (single.isBlank()) emptyList() else listOf(single)
    }

    /**
     * Find the first block index (0-based) whose text matches the query.
     * Matching is consistent with DB search: we normalize both sides with `TextSanitizer.normalizeForSearch`.
     */
    fun findFirstMatchingBlockIndex(blocksJson: String, query: String): Int? {
        val q = query.trim()
        if (q.isBlank()) return null

        val qNorm = TextSanitizer.normalizeForSearch(q).lowercase()
        if (qNorm.isBlank()) return null
        val qNoSpace = qNorm.replace(Regex("\\s+"), "")

        val blocks = extractBlockPlainTexts(blocksJson)
        if (blocks.isEmpty()) return null
        for ((idx, t) in blocks.withIndex()) {
            if (t.isBlank()) continue
            val tNorm = TextSanitizer.normalizeForSearch(t).lowercase()
            if (tNorm.contains(qNorm)) return idx
            if (qNoSpace.isNotBlank()) {
                val tNoSpace = tNorm.replace(Regex("\\s+"), "")
                if (tNoSpace.contains(qNoSpace)) return idx
            }
        }
        return null
    }

    fun findFirstMatchingBlockId(blocksJson: String, query: String): String? {
        val idx = findFirstMatchingBlockIndex(blocksJson, query) ?: return null
        val ids = computeStableBlockIds(blocksJson)
        return ids.getOrNull(idx)
    }

    fun extractPlainText(blocksJson: String): String {
        val root = parseRoot(blocksJson) ?: return ""

        val out = StringBuilder(2048)

        fun appendLine(s: String) {
            val t = s.trim()
            if (t.isBlank()) return
            if (out.isNotEmpty()) out.append('\n')
            out.append(t)
        }

        fun visit(el: JsonElement?) {
            if (el == null || el.isJsonNull) return
            when {
                el.isJsonPrimitive -> {
                    if (el.asJsonPrimitive.isString) {
                        appendLine(el.asString)
                    }
                }

                el.isJsonArray -> {
                    val arr: JsonArray = el.asJsonArray
                    for (child in arr) visit(child)
                }

                el.isJsonObject -> {
                    val obj: JsonObject = el.asJsonObject

                    // Prefer real payload fields and avoid indexing block metadata.
                    obj.get("code")?.let { codeEl ->
                        if (codeEl.isJsonPrimitive && codeEl.asJsonPrimitive.isString) {
                            appendLine(codeEl.asString)
                            return
                        }
                    }

                    // Prefer specific keys if present.
                    listOf("text", "title", "content", "caption", "alt").forEach { key ->
                        val v = obj.get(key)
                        if (v != null && v.isJsonPrimitive && v.asJsonPrimitive.isString) {
                            appendLine(v.asString)
                        }
                    }

                    // Table-like cell arrays.
                    val cells = obj.get("cells")
                    if (cells != null) visit(cells)

                    // TableBlock schema uses `rows`.
                    val rows = obj.get("rows")
                    if (rows != null) visit(rows)

                    // Rich-text spans often hold text.
                    val spans = obj.get("spans")
                    if (spans != null) visit(spans)

                    // Generic recursion to catch nested structures.
                    for ((k, v) in obj.entrySet()) {
                        val key = k.trim().lowercase()
                        if (key in META_KEYS_TO_SKIP) continue
                        if (key == "code") continue
                        visit(v)
                    }
                }
            }
        }

        visit(root)

        return out.toString()
    }
}
