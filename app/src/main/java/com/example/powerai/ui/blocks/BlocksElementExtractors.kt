package com.example.powerai.ui.blocks
import com.example.powerai.core.model.TableCell

import com.example.powerai.core.model.util.BlocksJsonUtils.asJsonObjectOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.booleanOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.boundingBoxAsStringOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.intOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.stringOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils
import com.google.gson.JsonObject
import com.google.gson.JsonElement

internal object BlocksElementExtractors {

    fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    fun extractStringArray(el: JsonElement?): List<String> {
        if (el == null || !el.isJsonArray) return emptyList()
        return el.asJsonArray.mapNotNull { item ->
            when {
                item.isJsonPrimitive && item.asJsonPrimitive.isString -> item.asString
                item.isJsonObject -> item.asJsonObject.stringOrNull("imageUri")
                    ?: item.asJsonObject.stringOrNull("src")
                else -> null
            }
        }
    }

    fun extractText(obj: JsonObject): String {
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

    fun extractListItems(obj: JsonObject): List<String> {
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

    fun extractTableRows(obj: JsonObject): List<List<String>> {
        val rowsEl = obj.get("rows") ?: obj.get("table_rows") ?: obj.get("cells")
        if (rowsEl != null && rowsEl.isJsonArray) {
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

        // Fallback: reconstruct rows from flat table_cells if present
        val cells = extractTableCells(obj)
        if (!cells.isNullOrEmpty()) {
            val maxRow = cells.maxOf { it.row }
            val rows = MutableList(maxRow + 1) { mutableListOf<String>() }
            for (c in cells.sortedWith(compareBy({ it.row }, { it.col }))) {
                rows[c.row].add(c.text)
            }
            return rows
        }

        return emptyList()
    }

    fun extractTableCells(obj: JsonObject): List<TableCell>? {
        val cellsEl = obj.get("cells") ?: obj.get("table_cells") ?: obj.get("table_structure")
        if (cellsEl == null || !cellsEl.isJsonArray) return null

        val arr = cellsEl.asJsonArray
        val size = arr.size()
        if (size == 0) return null

        val out = ArrayList<TableCell>(size)
        for (i in 0 until size) {
            val co = arr[i].asJsonObjectOrNull() ?: continue
            val row = co.intOrNull("row") ?: co.intOrNull("r") ?: continue
            val col = co.intOrNull("col") ?: co.intOrNull("c") ?: co.intOrNull("column") ?: continue
            val text = extractText(co)
            if (text.isBlank() && !co.has("rowSpan") && !co.has("colSpan")) continue

            out.add(
                TableCell(
                    row = row,
                    col = col,
                    rowSpan = co.intOrNull("rowSpan") ?: co.intOrNull("rs") ?: 1,
                    colSpan = co.intOrNull("colSpan") ?: co.intOrNull("cs") ?: 1,
                    text = text,
                    isHeader = co.booleanOrNull("isHeader") ?: co.booleanOrNull("h") ?: false,
                    alignment = co.stringOrNull("alignment"),
                    boundingBox = co.boundingBoxAsStringOrNull() ?: co.get("bbox")?.toString(),
                    confidence = co.get("confidence")?.asJsonPrimitive?.takeIf { it.isNumber }?.asFloat
                        ?: co.get("score")?.asJsonPrimitive?.takeIf { it.isNumber }?.asFloat
                )
            )
        }
        return out.takeIf { it.isNotEmpty() }
    }
}
