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
            // ڣǿưѿɱ񽵼Ϊ飬 UI ˽ TablePlugin Σ·
            degradeUnsafeTablesToCodeBlock = true,
            // ڲԽ⿪ ``` ı񣨱Ĵ飩
            unfenceTableCodeBlocks = false
        )
    }

    /**
     * UI רã
     * - ᳢԰ѡڹܱ ``` ı顱⿪
     * - Կɱʹô齵ͼƬҲʧǽΪͨı
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

            if (!MarkdownTableUtils.looksLikeTableRow(line)) {
                out.add(line)
                i += 1
                continue
            }

            // Gather a candidate table block: consecutive table-ish rows.
            val start = i
            var endExclusive = i
            while (endExclusive < lines.size && MarkdownTableUtils.looksLikeTableRow(lines[endExclusive])) {
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
        // ϴɿհ׵ԭĲհףֱӻˣҳʾհס
        return if (result.isBlank() && preprocessed.isNotBlank()) preprocessed else result
    }

    private fun shouldDegradeTable(maxCols: Int, blockLines: List<String>): Boolean =
        maxCols <= 1

    private fun degradeTableWith(blockLines: List<String>, asCodeBlock: Boolean): List<String> =
        if (asCodeBlock) wrapAsCodeBlock(blockLines) else blockLines

    private fun wrapAsCodeBlock(lines: List<String>): List<String> {
        // ôǿơҪڹȾ׶ε쳣㣩
        // ʹ text fence﷨
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
        // ΪͨıУ '|' ͷ TablePlugin 룬
        // ڵͼƬ﷨ ![]() Կɱء
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
            val isTableLike = inner.size >= 2 && inner.all { MarkdownTableUtils.looksLikeTableRow(it) }
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

        val parsedRows = blockLines.map { MarkdownTableUtils.splitRow(it) }
        if (parsedRows.any { it.isEmpty() }) return null

        // A markdown table should have a separator row.
        val separatorIndex = parsedRows.indexOfFirst { MarkdownTableUtils.isSeparatorRow(it) }
        if (separatorIndex < 0) return null

        val maxCols = parsedRows.maxOf { it.size }
        // ָдڵ˻ǿƽ TablePlugin 쳣
        if (shouldDegradeTable(maxCols, blockLines)) {
            return degradeTableWith(blockLines, degradeUnsafeTablesToCodeBlock)
        }

        val paddedRows = padRowsToMaxColumns(parsedRows, maxCols)
        val emptyCol = identifyEmptyColumns(paddedRows, separatorIndex, maxCols)
        val keepIndices = (0 until maxCols).filter { c -> !emptyCol[c] }
        
        // ɾֻʣ 0/1 У˵񡱼ȫջṹ쳣Ϊȡ
        if (keepIndices.size < 2) {
            return degradeTableWith(blockLines, degradeUnsafeTablesToCodeBlock)
        }

        // Ᵽ̫ʱ TablePlugin п/׳⣬ֱӽ
        if (keepIndices.size > 60) {
            return if (degradeUnsafeTablesToCodeBlock) {
                wrapAsCodeBlock(blockLines)
            } else {
                flattenAsPlainTextRows(paddedRows, separatorIndex, keepIndices)
            }
        }

        val rebuiltPadded = removeEmptyDataRows(paddedRows, separatorIndex, keepIndices)
        
        // ɾк󣬱ֻʣ header+separator٣˵ȫգ TablePlugin
        if (rebuiltPadded.size <= 2) {
            return if (degradeUnsafeTablesToCodeBlock) {
                wrapAsCodeBlock(blockLines)
            } else {
                flattenAsPlainTextRows(paddedRows, separatorIndex, keepIndices)
            }
        }

        // ¼ separatorIndexΪɾ˲У
        val newSeparatorIndex = rebuiltPadded.indexOfFirst { MarkdownTableUtils.isSeparatorRow(it) }
        if (newSeparatorIndex < 0) {
            return degradeTableWith(blockLines, degradeUnsafeTablesToCodeBlock)
        }

        return rebuildTableLines(rebuiltPadded, newSeparatorIndex, keepIndices)
    }

    private fun padRowsToMaxColumns(parsedRows: List<List<String>>, maxCols: Int): List<List<String>> {
        return parsedRows.map { row ->
            if (row.size == maxCols) row else row + List(maxCols - row.size) { "" }
        }
    }

    private fun identifyEmptyColumns(
        paddedRows: List<List<String>>,
        separatorIndex: Int,
        maxCols: Int
    ): BooleanArray {
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
        return emptyCol
    }

    private fun removeEmptyDataRows(
        paddedRows: List<List<String>>,
        separatorIndex: Int,
        keepIndices: List<Int>
    ): List<List<String>> {
        // ؼ޸ɾȫաУTableRowSpan.draw ܻ 0 ³㣩
        // 뱣 header( 0 )  separator С
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
        return rebuiltPadded
    }

    private fun rebuildTableLines(
        rebuiltPadded: List<List<String>>,
        newSeparatorIndex: Int,
        keepIndices: List<Int>
    ): List<String> {
        val rebuilt = ArrayList<String>(rebuiltPadded.size)
        for (r in rebuiltPadded.indices) {
            val row = rebuiltPadded[r]
            if (r == newSeparatorIndex) {
                // Rebuild separator using the original tokens when possible, else default to ---.
                val sepCells = keepIndices.map { c ->
                    val t = row[c].trim()
                    if (t.isNotEmpty()) t else "---"
                }
                rebuilt.add(MarkdownTableUtils.joinRow(sepCells))
            } else {
                val cells = keepIndices.map { c -> row[c] }
                rebuilt.add(MarkdownTableUtils.joinRow(cells))
            }
        }
        return rebuilt
    }
}
