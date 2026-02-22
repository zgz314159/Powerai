package com.example.powerai.data.importer

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object BlocksJsonPatcher {

    fun applyMarkdownTableToBlock(
        blocksJson: String,
        blockId: String,
        markdown: String
    ): String? {
        val targetId = blockId.trim()
        if (blocksJson.isBlank() || targetId.isBlank() || markdown.isBlank()) return null

        val root = try {
            JsonParser().parse(blocksJson)
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

        if (blocksArray == null || blocksArray.size() == 0) return null

        val rows = parseMarkdownTableRows(markdown) ?: return null

        for (el in blocksArray) {
            val obj = el.asJsonObjectOrNull() ?: continue
            val id = obj.stringOrNull("id") ?: obj.stringOrNull("blockId")
            if (id?.trim() != targetId) continue

            obj.addProperty("type", "table")
            obj.add("rows", rowsToJson(rows))
            // Keep the original markdown as a hint/debug field (ignored by current parser/renderer).
            obj.addProperty("markdown", markdown)

            return root.toString()
        }

        return null
    }

    private fun parseMarkdownTableRows(markdown: String): List<List<String>>? {
        val lines = markdown
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.size < 2) return null

        val out = ArrayList<List<String>>()
        for (line in lines) {
            if (!line.contains('|')) continue
            if (isSeparatorLine(line)) continue

            val noEdge = line.trim().removePrefix("|").removeSuffix("|")
            val cols = noEdge.split('|').map { it.trim() }
            if (cols.isEmpty() || cols.all { it.isBlank() }) continue
            out.add(cols)
        }

        if (out.size < 2) return null
        return out
    }

    private fun isSeparatorLine(line: String): Boolean {
        // Matches: | --- | :---: | ---: |
        val s = line.trim()
        val re = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$")
        return re.matches(s)
    }

    private fun rowsToJson(rows: List<List<String>>): JsonArray {
        val arr = JsonArray()
        for (row in rows) {
            val rowArr = JsonArray()
            for (cell in row) rowArr.add(cell)
            arr.add(rowArr)
        }
        return arr
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
}
