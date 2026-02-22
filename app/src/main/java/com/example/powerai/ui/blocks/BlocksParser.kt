package com.example.powerai.ui.blocks

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest

object BlocksParser {
    private const val TAG = "PowerAi.Trace"

    fun parseBlocks(json: String?): List<KnowledgeBlock>? {
        if (json.isNullOrBlank()) {
            Log.d(TAG, "parseBlocks: input blank or null")
            return null
        }

        val startMs = System.currentTimeMillis()
        Log.d(TAG, "parseBlocks START length=${json.length}")

        val root = try {
            JsonParser().parse(json)
        } catch (_: Throwable) {
            return null
        }

        val blocksArray: JsonArray? = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                when {
                    obj.get("blocks")?.isJsonArray == true -> obj.getAsJsonArray("blocks")
                    obj.get("contentBlocks")?.isJsonArray == true -> obj.getAsJsonArray("contentBlocks")
                    obj.get("content_blocks")?.isJsonArray == true -> obj.getAsJsonArray("content_blocks")
                    else -> null
                }
            }
            else -> null
        }

        if (blocksArray == null || blocksArray.size() == 0) {
            Log.d(TAG, "parseBlocks: no blocks found")
            return emptyList()
        }

        val out = ArrayList<KnowledgeBlock>(blocksArray.size())
        for (el in blocksArray) {
            val obj = el.asJsonObjectOrNull() ?: continue
            out.add(parseBlockObject(obj))
        }
        val result = ensureStableIds(out)
        Log.d(TAG, "parseBlocks DONE count=${result.size} took=${System.currentTimeMillis() - startMs}ms")
        return result
    }

    private fun ensureStableIds(blocks: List<KnowledgeBlock>): List<KnowledgeBlock> {
        if (blocks.isEmpty()) return blocks

        fun sha1Hex(s: String): String {
            val md = MessageDigest.getInstance("SHA-1")
            val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) sb.append(String.format("%02x", b))
            return sb.toString()
        }

        val seen = HashMap<String, Int>(blocks.size)
        val out = ArrayList<KnowledgeBlock>(blocks.size)
        for (b in blocks) {
            if (!b.id.isNullOrBlank()) {
                out.add(b)
                continue
            }
            val type = when (b) {
                is TextBlock -> "text"
                is ImageBlock -> "image"
                is ListBlock -> if (b.ordered) "ol" else "ul"
                is TableBlock -> "table"
                is CodeBlock -> "code"
                is UnknownBlock -> (b.type ?: "unknown")
            }
            val text = when (b) {
                is TextBlock -> b.text
                is ImageBlock -> listOfNotNull(b.src, b.alt, b.caption).joinToString(" ")
                is ListBlock -> b.items.joinToString("\n")
                is TableBlock -> b.rows.joinToString("\n") { row -> row.joinToString("\t") }
                is CodeBlock -> b.code
                is UnknownBlock -> b.rawText
            }
            val norm = ("$type|$text").lowercase().replace(Regex("\\s+"), " ").trim()
            val base = sha1Hex(norm).take(12)
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            val id = "b_${base}_$n"
            out.add(
                when (b) {
                    is TextBlock -> b.copy(id = id)
                    is ImageBlock -> b.copy(id = id)
                    is ListBlock -> b.copy(id = id)
                    is TableBlock -> b.copy(id = id)
                    is CodeBlock -> b.copy(id = id)
                    is UnknownBlock -> b.copy(id = id)
                }
            )
        }
        return out
    }

    private fun parseBlockObject(obj: JsonObject): KnowledgeBlock {
        val id = obj.stringOrNull("id") ?: obj.stringOrNull("blockId")
        val type = obj.stringOrNull("type") ?: obj.stringOrNull("blockType")
        val t = type?.trim()?.lowercase()

        val bbox = obj.boundingBoxAsStringOrNull()

        val pageNumber = obj.intOrNull("pageNumber")
            ?: obj.intOrNull("page")
            ?: obj.intOrNull("p")

        val imageUri = obj.stringOrNull("imageUri")
            ?: obj.stringOrNull("image_uri")
            ?: obj.stringOrNull("snapshotUri")
            ?: obj.stringOrNull("snapshot_uri")

        return when (t) {
            "p", "paragraph", "text" -> {
                val text = extractText(obj)
                TextBlock(id = id, text = text, style = TextBlock.TextStyle.Paragraph, boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri)
            }
            "h1", "heading1", "heading_1", "title" -> {
                TextBlock(id = id, text = extractText(obj), style = TextBlock.TextStyle.Heading1, boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri)
            }
            "h2", "heading2", "heading_2" -> {
                TextBlock(id = id, text = extractText(obj), style = TextBlock.TextStyle.Heading2, boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri)
            }
            "h3", "heading3", "heading_3" -> {
                TextBlock(id = id, text = extractText(obj), style = TextBlock.TextStyle.Heading3, boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri)
            }
            "quote", "blockquote" -> {
                TextBlock(id = id, text = extractText(obj), style = TextBlock.TextStyle.Quote, boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri)
            }
            "image", "img" -> {
                val src = obj.stringOrNull("src").orEmpty().ifBlank { obj.stringOrNull("url").orEmpty() }
                val alt = obj.stringOrNull("alt")
                val caption = obj.stringOrNull("caption")
                ImageBlock(id = id, src = src, alt = alt, caption = caption, boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri)
            }
            "list", "bullet_list", "ordered_list" -> {
                val ordered = when {
                    obj.booleanOrNull("ordered") != null -> obj.booleanOrNull("ordered") == true
                    t == "ordered_list" -> true
                    else -> false
                }
                val items = extractListItems(obj)
                ListBlock(id = id, ordered = ordered, items = items, boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri)
            }
            "table" -> {
                val rows = extractTableRows(obj)
                TableBlock(id = id, rows = rows, boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri)
            }
            "code", "code_block" -> {
                val code = obj.stringOrNull("code").orEmpty().ifBlank { obj.stringOrNull("text").orEmpty() }
                val language = obj.stringOrNull("language")
                CodeBlock(id = id, code = code, language = language, boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri)
            }
            else -> {
                val text = extractText(obj)
                UnknownBlock(id = id, type = type, rawText = text, boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri)
            }
        }
    }

    private fun extractText(obj: JsonObject): String {
        obj.stringOrNull("text")?.let { if (it.isNotBlank()) return it }
        obj.stringOrNull("title")?.let { if (it.isNotBlank()) return it }
        obj.stringOrNull("content")?.let { if (it.isNotBlank()) return it }

        // spans: [{text:"..."}, ...]
        val spans = obj.get("spans")
        if (spans != null && spans.isJsonArray) {
            val sb = StringBuilder()
            for (s in spans.asJsonArray) {
                val so = s.asJsonObjectOrNull() ?: continue
                val st = so.stringOrNull("text") ?: continue
                if (st.isBlank()) continue
                if (sb.isNotEmpty()) sb.append("")
                sb.append(st)
            }
            val merged = sb.toString().trim()
            if (merged.isNotBlank()) return merged
        }

        // fallback: try caption/alt
        obj.stringOrNull("caption")?.let { if (it.isNotBlank()) return it }
        obj.stringOrNull("alt")?.let { if (it.isNotBlank()) return it }

        return ""
    }

    private fun extractListItems(obj: JsonObject): List<String> {
        val out = ArrayList<String>()
        val itemsEl = obj.get("items")
        if (itemsEl != null && itemsEl.isJsonArray) {
            for (it in itemsEl.asJsonArray) {
                when {
                    it.isJsonPrimitive && it.asJsonPrimitive.isString -> out.add(it.asString)
                    it.isJsonObject -> {
                        val io = it.asJsonObject
                        val txt = io.stringOrNull("text").orEmpty().ifBlank { io.stringOrNull("content").orEmpty() }
                        if (txt.isNotBlank()) out.add(txt)
                    }
                }
            }
        }
        return out
    }

    private fun extractTableRows(obj: JsonObject): List<List<String>> {
        val rowsEl = obj.get("rows") ?: obj.get("cells")
        if (rowsEl == null || !rowsEl.isJsonArray) return emptyList()

        val out = ArrayList<List<String>>()
        for (rowEl in rowsEl.asJsonArray) {
            if (!rowEl.isJsonArray) continue
            val row = ArrayList<String>()
            for (cellEl in rowEl.asJsonArray) {
                val cellText = when {
                    cellEl.isJsonPrimitive && cellEl.asJsonPrimitive.isString -> cellEl.asString
                    cellEl.isJsonObject -> extractText(cellEl.asJsonObject)
                    else -> ""
                }
                row.add(cellText)
            }
            out.add(row)
        }
        return out
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        if (this.isJsonObject) this.asJsonObject else null

    private fun JsonObject.stringOrNull(key: String): String? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        val prim = el.asJsonPrimitive
        if (!prim.isString) return null
        return prim.asString
    }

    private fun JsonObject.booleanOrNull(key: String): Boolean? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        val prim = el.asJsonPrimitive
        if (!prim.isBoolean) return null
        return prim.asBoolean
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        val prim = el.asJsonPrimitive
        return when {
            prim.isNumber -> prim.asInt
            prim.isString -> prim.asString.toIntOrNull()
            else -> null
        }
    }

    private fun JsonObject.boundingBoxAsStringOrNull(): String? {
        val el = get("boundingBox") ?: return null
        if (el.isJsonNull) return null

        return when {
            el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString
            else -> el.toString()
        }.trim().takeIf { it.isNotBlank() }
    }
}
