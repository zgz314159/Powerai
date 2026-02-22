package com.example.powerai.data.importer

/**
 * Normalizes markdown tables that come from DOCX conversions.
 *
 * Primary goal: reduce extremely wide tables produced by merged cells by removing
 * columns that are entirely empty across the whole table.
 *
 * This runs during JSON import (non-UI layer) so the UI only renders cleaner markdown.
 */
object MarkdownTableNormalizer {

    fun normalizeMarkdownTables(input: String): String {
        return normalizeMarkdownTablesInternal(
            input = input,
            // 导入期：强制把可疑表格降级为代码块，尽量避免 UI 端进入 TablePlugin 危险路径。
            degradeUnsafeTablesToCodeBlock = true,
            // 导入期不尝试解开 ``` 包裹的表格（避免误伤真正的代码块）。
            unfenceTableCodeBlocks = false
        )
    }

    /**
     * UI 侧专用：
     * - 会尝试把“仅用于规避崩溃而被 ``` 包裹的表格块”解开
     * - 对可疑表格不使用代码块降级（否则图片也会消失），而是降级为普通文本行
     */
    fun normalizeMarkdownTablesForUi(input: String): String {
        return normalizeMarkdownTablesInternal(
            input = input,
            degradeUnsafeTablesToCodeBlock = false,
            unfenceTableCodeBlocks = true
        )
    }

    private fun normalizeMarkdownTablesInternal(
        input: String,
        degradeUnsafeTablesToCodeBlock: Boolean,
        unfenceTableCodeBlocks: Boolean
    ): String {
        if (input.isBlank()) return input

        val preprocessed = if (unfenceTableCodeBlocks) {
            unfenceTableLikeCodeBlocks(input)
        } else {
            input
        }

        val lines = preprocessed.split("\n")
        val out = ArrayList<String>(lines.size)

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            if (!looksLikeTableRow(line)) {
                out.add(line)
                i += 1
                continue
            }

            // Gather a candidate table block: consecutive table-ish rows.
            val start = i
            var endExclusive = i
            while (endExclusive < lines.size && looksLikeTableRow(lines[endExclusive])) {
                endExclusive += 1
            }

            val block = lines.subList(start, endExclusive)
            val normalized = normalizeTableBlockOrNull(block, degradeUnsafeTablesToCodeBlock)
            if (normalized != null) {
                out.addAll(normalized)
                i = endExclusive
            } else {
                // Not an actual markdown table (e.g., a single pipe line). Emit as-is.
                out.addAll(block)
                i = endExclusive
            }
        }

        val result = out.joinToString("\n")
        // 防御：如果清洗后变成空白但原文不空白，直接回退，避免详情页显示空白。
        return if (result.isBlank() && preprocessed.isNotBlank()) preprocessed else result
    }

    private fun looksLikeTableRow(line: String): Boolean {
        val s = line.trim()
        if (!s.startsWith("|")) return false
        // Needs at least 2 pipes to look like a row.
        val pipeCount = s.count { it == '|' }
        return pipeCount >= 2
    }

    private fun isSeparatorRow(cells: List<String>): Boolean {
        // Typical markdown separator row like: |---|:---:|---|
        if (cells.isEmpty()) return false
        var hasDash = false
        for (cell in cells) {
            val t = cell.trim()
            if (t.isEmpty()) {
                // 容忍空分隔单元格（常见于“表头有额外空列，但分隔行缺了对应 ---”的脏数据）。
                continue
            }
            // 允许 ':' 用于对齐，但必须主要由 '-' 组成，且至少 3 个 '-' 才算合法分隔。
            if (!t.all { ch -> ch == '-' || ch == ':' }) return false
            val dashCount = t.count { it == '-' }
            if (dashCount >= 3) hasDash = true
        }
        // 避免把全空行误判成分隔行。
        return hasDash
    }

    private fun splitRow(line: String): List<String> {
        // 支持两种写法：
        // 1) |a|b|c|  (有尾随 '|')
        // 2) |a|b|c   (无尾随 '|')
        // 注意：Kotlin/Java 的 split 默认会丢弃“尾随空字段”，这会让像 "|a|b|||" 这种
        // 末尾空列的行被错误解析为更少的列，从而导致 header/separator 列数不一致无法修复。
        val raw = line.trim()
        if (raw.isEmpty()) return emptyList()
        if (!raw.startsWith("|")) return emptyList()

        // 只去掉一个首/尾分隔符，让其余的 '|' 作为空单元格被保留下来。
        var content = raw.removePrefix("|")
        if (content.endsWith("|")) {
            content = content.dropLast(1)
        }

        // Kotlin 的 split(limit) 不允许 -1；这里手动切分以保留尾随空字段。
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

    private fun joinRow(cells: List<String>): String {
        return buildString {
            append('|')
            cells.forEach { c ->
                append(c.trim())
                append('|')
            }
        }
    }

    private fun wrapAsCodeBlock(lines: List<String>): List<String> {
        // 用代码块强制“不要当表格解析”，用于规避渲染阶段的异常（例如除零）。
        // 使用 text fence，避免语法高亮依赖。
        val out = ArrayList<String>(lines.size + 2)
        out.add("```")
        out.addAll(lines)
        out.add("```")
        return out
    }

    private fun flattenAsPlainTextRows(
        paddedRows: List<List<String>>,
        separatorIndex: Int,
        keepIndices: List<Int>
    ): List<String> {
        // 降级为普通文本行（不以 '|' 开头），这样 TablePlugin 不会介入，
        // 但行内的图片语法 ![]() 仍可被解析并加载。
        val out = ArrayList<String>()
        for (r in paddedRows.indices) {
            if (r == separatorIndex) continue
            val row = paddedRows[r]
            val cells = keepIndices.map { c -> row[c].trim() }
            if (cells.all { it.isEmpty() }) continue
            out.add(cells.joinToString(" | "))
        }
        return out
    }

    private fun unfenceTableLikeCodeBlocks(input: String): String {
        val lines = input.split("\n")
        val out = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trim() != "```") {
                out.add(line)
                i++
                continue
            }

            // find closing fence
            var j = i + 1
            while (j < lines.size && lines[j].trim() != "```") {
                j++
            }
            if (j >= lines.size) {
                // no closing fence
                out.add(line)
                i++
                continue
            }

            val inner = lines.subList(i + 1, j)
            val isTableLike = inner.size >= 2 && inner.all { looksLikeTableRow(it) }
            if (isTableLike) {
                // unwrap
                out.addAll(inner)
            } else {
                // keep as-is
                out.add(line)
                out.addAll(inner)
                out.add(lines[j])
            }
            i = j + 1
        }
        return out.joinToString("\n")
    }

    private fun normalizeTableBlockOrNull(
        blockLines: List<String>,
        degradeUnsafeTablesToCodeBlock: Boolean
    ): List<String>? {
        if (blockLines.size < 2) return null

        val parsedRows = blockLines.map { splitRow(it) }
        if (parsedRows.any { it.isEmpty() }) return null

        // A markdown table should have a separator row.
        val separatorIndex = parsedRows.indexOfFirst { isSeparatorRow(it) }
        if (separatorIndex < 0) return null

        val maxCols = parsedRows.maxOf { it.size }
        // 分隔行存在但列数退化，强制降级，避免 TablePlugin 异常。
        if (maxCols <= 1) {
            return if (degradeUnsafeTablesToCodeBlock) wrapAsCodeBlock(blockLines) else blockLines
        }

        val paddedRows = parsedRows.map { row ->
            if (row.size == maxCols) row else row + List(maxCols - row.size) { "" }
        }

        // Determine empty columns across all non-separator rows.
        val emptyCol = BooleanArray(maxCols) { true }
        for (r in paddedRows.indices) {
            if (r == separatorIndex) continue
            val row = paddedRows[r]
            for (c in 0 until maxCols) {
                if (row[c].trim().isNotEmpty()) {
                    emptyCol[c] = false
                }
            }
        }

        val keepIndices = (0 until maxCols).filter { c -> !emptyCol[c] }
        // 如果删完只剩 0/1 列，说明这个“表格”几乎全空或结构异常，降级为代码块更稳。
        if (keepIndices.size < 2) {
            return if (degradeUnsafeTablesToCodeBlock) wrapAsCodeBlock(blockLines) else blockLines
        }

        // 额外保护：列太多时 TablePlugin 计算列宽/缩放容易出问题，直接降级。
        // 这里阈值取保守值，避免 UI 横向滚动极端宽导致的渲染不稳定。
        if (keepIndices.size > 60) {
            return if (degradeUnsafeTablesToCodeBlock) {
                wrapAsCodeBlock(blockLines)
            } else {
                flattenAsPlainTextRows(paddedRows, separatorIndex, keepIndices)
            }
        }

        // 关键修复：删除“整行全空”的数据行（TableRowSpan.draw 里可能会对 0 宽度求比例导致除零）
        // 但必须保留 header(第 0 行) 和 separator 行。
        val rebuiltPadded = ArrayList<List<String>>(paddedRows.size)
        for (r in paddedRows.indices) {
            val row = paddedRows[r]
            if (r == separatorIndex || r == 0) {
                rebuiltPadded.add(row)
                continue
            }
            val kept = keepIndices.map { c -> row[c].trim() }
            if (kept.all { it.isEmpty() }) {
                // drop empty row
                continue
            }
            rebuiltPadded.add(row)
        }

        // 如果删除空行后，表格只剩 header+separator（或更少），说明数据行全空，避免 TablePlugin。
        if (rebuiltPadded.size <= 2) {
            return if (degradeUnsafeTablesToCodeBlock) {
                wrapAsCodeBlock(blockLines)
            } else {
                flattenAsPlainTextRows(paddedRows, separatorIndex, keepIndices)
            }
        }

        // 重新计算 separatorIndex（因为可能删掉了部分行）
        val newSeparatorIndex = rebuiltPadded.indexOfFirst { isSeparatorRow(it) }
        if (newSeparatorIndex < 0) {
            return if (degradeUnsafeTablesToCodeBlock) wrapAsCodeBlock(blockLines) else blockLines
        }

        val rebuilt = ArrayList<String>(rebuiltPadded.size)
        for (r in rebuiltPadded.indices) {
            val row = rebuiltPadded[r]
            if (r == newSeparatorIndex) {
                // Rebuild separator using the original tokens when possible, else default to ---.
                val sepCells = keepIndices.map { c ->
                    val t = row[c].trim()
                    if (t.isNotEmpty()) t else "---"
                }
                rebuilt.add(joinRow(sepCells))
            } else {
                val cells = keepIndices.map { c -> row[c] }
                rebuilt.add(joinRow(cells))
            }
        }

        return rebuilt
    }
}
