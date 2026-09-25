package com.example.powerai.data.importer

/**
 * Utilities for parsing and composing markdown table rows.
 *
 * Used by [MarkdownTableNormalizer] to process DOCX-converted markdown tables.
 */
internal object MarkdownTableUtils {

    /**
     * Checks if a line looks like a markdown table row.
     *
     * A table row should start with '|' and contain at least 2 pipes total.
     *
     * @param line The line to check
     * @return true if the line resembles a table row
     */
    fun looksLikeTableRow(line: String): Boolean {
        val s = line.trim()
        if (!s.startsWith("|")) return false
        // Needs at least 2 pipes to look like a row.
        val pipeCount = s.count { it == '|' }
        return pipeCount >= 2
    }

    /**
     * Checks if a row is a markdown table separator row (e.g., |---|:---:|---|).
     *
     * A separator row must have at least 3 dashes in at least one cell,
     * and all non-empty cells must consist only of '-' and ':' characters.
     *
     * @param cells The parsed cells of the row
     * @return true if this is a separator row
     */
    fun isSeparatorRow(cells: List<String>): Boolean {
        // Typical markdown separator row like: |---|:---:|---|
        if (cells.isEmpty()) return false
        var hasDash = false
        for (cell in cells) {
            val t = cell.trim()
            if (t.isEmpty()) {
                // 容忍空分隔单元格（常见于"表头有额外空列，但分隔行缺了对应 ---"的脏数据）
                continue
            }
            // 允许 ':' 用于对齐，但必须主要'-' 组成，且至少 3 '-' 才算合法分隔
            if (!t.all { ch -> ch == '-' || ch == ':' }) return false
            val dashCount = t.count { it == '-' }
            if (dashCount >= 3) hasDash = true
        }
        // 避免把全空行误判成分隔行
        return hasDash
    }

    /**
     * Splits a markdown table row into cells.
     *
     * Supports both trailing and non-trailing pipe formats:
     * - |a|b|c|  (with trailing '|')
     * - |a|b|c   (without trailing '|')
     *
     * Preserves trailing empty cells (e.g., |a|b||| yields 4 cells).
     * Handles escaped pipes (\|) as literal characters.
     *
     * @param line The raw table row line
     * @return List of cell contents (may contain empty strings)
     */
    fun splitRow(line: String): List<String> {
        // 支持两种写法
        // 1) |a|b|c|  (有尾'|')
        // 2) |a|b|c   (无尾'|')
        // 注意：Kotlin/Java split 默认会丢尾随空字，这会让"|a|b|||" 这种
        // 末尾空列的行被错误解析为更少的列，从而导header/separator 列数不一致无法修复
        val raw = line.trim()
        if (raw.isEmpty()) return emptyList()
        if (!raw.startsWith("|")) return emptyList()

        // 只去掉一个首/尾分隔符，让其余'|' 作为空单元格被保留下来
        var content = raw.removePrefix("|")
        if (content.endsWith("|")) {
            content = content.dropLast(1)
        }

        // Kotlin split(limit) 不允-1；这里手动切分以保留尾随空字段
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (idx in content.indices) {
            val ch = content[idx]
            val isEscapedPipe = ch == '|' && idx > 0 && content[idx - 1] == '\\'
            if (ch == '|' && !isEscapedPipe) {
                out.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out
    }

    /**
     * Joins a list of cell values back into a markdown table row.
     *
     * Trims each cell and wraps with pipes: |cell1|cell2|cell3|
     *
     * @param cells The list of cell contents
     * @return A properly formatted markdown table row
     */
    fun joinRow(cells: List<String>): String {
        return buildString {
            append('|')
            cells.forEach { c ->
                append(c.trim())
                append('|')
            }
        }
    }
}
