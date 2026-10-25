package com.example.powerai.ui.screen.detail

// ============================================================================
// SECTION 1: TABLE BLOCK PROTECTION (was BlockProtector.kt)
// ============================================================================

internal object TableBlockProtector {
    private const val PLACEHOLDER_PREFIX = "@@TABLE_BLOCK_"
    private const val PLACEHOLDER_SUFFIX = "@@"

    data class ProtectedBlocks(
        val protectedText: String,
        val blocks: List<String>
    )

    fun protect(markdown: String): ProtectedBlocks {
        val lines = markdown.split('\n')
        val tableBlocks = ArrayList<String>()
        val rebuilt = StringBuilder(markdown.length)

        var i = 0
        while (i < lines.size) {
            if (!isTableLine(lines[i])) {
                rebuilt.append(lines[i])
                if (i != lines.lastIndex) rebuilt.append('\n')
                i++
                continue
            }

            val start = i
            while (i < lines.size && isTableLine(lines[i])) {
                i++
            }

            val block = lines.subList(start, i).joinToString("\n")
            val idx = tableBlocks.size
            tableBlocks.add(block)

            rebuilt.append(PLACEHOLDER_PREFIX).append(idx).append(PLACEHOLDER_SUFFIX)
            if (i != lines.size) rebuilt.append('\n')
        }

        return ProtectedBlocks(
            protectedText = rebuilt.toString(),
            blocks = tableBlocks
        )
    }

    fun restore(text: String, blocks: List<String>): String {
        var restored = text
        for (idx in blocks.indices) {
            restored = restored.replace("$PLACEHOLDER_PREFIX${idx}$PLACEHOLDER_SUFFIX", blocks[idx])
        }
        return restored
    }
}

// ============================================================================
// SECTION 2: TABLE BLOCK WRITER (was BlockWriter.kt)
// ============================================================================

internal object TableBlockWriter {
    fun ensureTwoBlankLinesBefore(out: MutableList<String>) {
        // Force blank lines before table block
        if (out.isNotEmpty() && out.last().isNotBlank()) {
            out.add("")
            out.add("")
            return
        }

        if (out.isNotEmpty() && out.last().isBlank()) {
            // ensure at least 2 blank lines
            if (out.size >= 2) {
                if (out[out.size - 2].isNotBlank()) out.add("")
            } else {
                out.add("")
            }
        }
    }

    fun appendTwoBlankLines(out: MutableList<String>) {
        // Force blank lines after table block
        out.add("")
        out.add("")
    }
}

// ============================================================================
// SECTION 3: TABLE BLOCK NORMALIZER (was BlockNormalizer.kt)
// ============================================================================

internal object TableBlockNormalizer {
    fun normalizeOrKeep(block: List<String>): List<String> {
        if (block.isEmpty()) return emptyList()

        val hasSep = block.size >= 2 && isSeparatorLine(block[1])
        if (!hasSep) return block

        val parsed = block.map { splitCellsPreserveTrailingEmpty(it) }
        val maxCols = parsed.maxOfOrNull { it.size } ?: 0
        val cols = maxCols.coerceAtLeast(1)

        val out = ArrayList<String>(block.size + 2)

        // Rebuild header (pad to max cols)
        val headerCells = (parsed.getOrNull(0) ?: emptyList()).let { row ->
            if (row.size >= cols) row.take(cols) else row + List(cols - row.size) { "" }
        }
        out.add(joinCells(headerCells))

        // Rebuild separator (always cols)
        out.add(buildSeparatorRow(cols))

        // Rebuild data rows (pad to cols)
        for (r in 2 until block.size) {
            val row = parsed.getOrNull(r) ?: emptyList()
            val padded = if (row.size >= cols) row.take(cols) else row + List(cols - row.size) { "" }
            out.add(joinCells(padded))
        }

        return out
    }
}

// ============================================================================
// SECTION 4: SEPARATOR HELPERS (was SeparatorHelpers.kt, NormalizeHelpers.kt)
// ============================================================================

private fun isTableLine(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("|") && t.count { it == '|' } >= 2
}

private fun isSeparatorLine(line: String): Boolean {
    val t = line.trim()
    if (!t.startsWith("|")) return false
    val separatorLineRegex = Regex("^\\|\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|\\s*$")
    return separatorLineRegex.matches(t)
}

private fun normalizeRowPipes(line: String): String {
    val t = line.trim()
    if (!t.startsWith("|")) return line
    return if (t.endsWith("|")) t else "$t|"
}

private fun pipeCount(line: String): Int = line.count { it == '|' }

private fun buildSeparatorRow(columns: Int): String {
    val c = columns.coerceAtLeast(1)
    return buildString {
        append('|')
        repeat(c) { append("---|") }
    }
}

private fun splitCellsPreserveTrailingEmpty(line: String): List<String> {
    val raw = line.trim()
    if (raw.isEmpty()) return emptyList()
    if (!raw.startsWith("|")) return emptyList()
    var content = raw.removePrefix("|")
    if (content.endsWith("|")) {
        content = content.dropLast(1)
    }
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

private fun joinCells(cells: List<String>): String {
    return buildString {
        append('|')
        for (c in cells) {
            val cell = c.trim().replace(Regex("\\s{2,}"), " ")
            append(cell)
            append('|')
        }
    }
}

// ============================================================================
// SECTION 5: SEPARATOR INSERTER (was SeparatorInserter.kt)
// ============================================================================

internal object TableSeparatorInserter {
    fun normalizeRowPipesAndInsertMissingSeparators(
        lines: MutableList<String>,
        separatorLineRegex: Regex
    ) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val t = line.trim()

            if (t.startsWith("|") && !separatorLineRegex.matches(t)) {
                lines[i] = normalizeRowPipes(t)
            }

            // Insert separator if a table row is followed by non-separator table row
            if (t.startsWith("|") && i + 1 < lines.size) {
                val nextT = lines[i + 1].trim()
                if (nextT.startsWith("|") && !separatorLineRegex.matches(nextT) && !separatorLineRegex.matches(t)) {
                    val cols = (pipeCount(lines[i]) - 1).coerceAtLeast(1)
                    lines.add(i + 1, buildSeparatorRow(cols))
                }
            }

            i++
        }
    }
}

// ============================================================================
// SECTION 6: SEPARATOR WIDTH NORMALIZER (was SeparatorWidthNormalizer.kt)
// ============================================================================

internal object TableSeparatorWidthNormalizer {
    fun normalizeSeparatorWidthsAndPadBlankLines(
        lines: MutableList<String>,
        separatorLineRegex: Regex
    ) {
        var i = 0
        while (i < lines.size) {
            val t = lines[i].trim()
            if (separatorLineRegex.matches(t)) {
                var maxPipes = pipeCount(t)

                var j = i - 1
                while (j >= 0) {
                    val lt = lines[j].trim()
                    if (lt.isBlank()) break
                    if (!lt.startsWith("|")) break
                    if (separatorLineRegex.matches(lt)) break
                    maxPipes = maxOf(
                        maxPipes,
                        pipeCount(normalizeRowPipes(lt))
                    )
                    j--
                }

                var k = i + 1
                while (k < lines.size) {
                    val lt = lines[k].trim()
                    if (lt.isBlank()) break
                    if (!lt.startsWith("|")) break
                    if (separatorLineRegex.matches(lt)) break
                    maxPipes = maxOf(
                        maxPipes,
                        pipeCount(normalizeRowPipes(lt))
                    )
                    k++
                }

                val columns = (maxPipes - 1).coerceAtLeast(1)
                lines[i] = buildSeparatorRow(columns)

                if (i - 1 >= 0 && lines[i - 1].isNotBlank()) {
                    lines.add(i, "")
                    i++
                }
                if (i + 1 < lines.size && lines[i + 1].isNotBlank()) {
                    lines.add(i + 1, "")
                } else if (i + 1 == lines.size) {
                    lines.add("")
                }
            }
            i++
        }
    }
}

// ============================================================================
// SECTION 7: SEPARATOR PROCESSING PIPELINE (was SeparatorsPipeline.kt)
// ============================================================================

internal object TableSeparatorPipeline {
    fun normalizeRowPipesAndInsertMissingSeparators(
        lines: MutableList<String>,
        separatorLineRegex: Regex
    ) {
        TableSeparatorInserter.normalizeRowPipesAndInsertMissingSeparators(lines, separatorLineRegex)
    }

    fun normalizeSeparatorWidthsAndPadBlankLines(
        lines: MutableList<String>,
        separatorLineRegex: Regex
    ) {
        TableSeparatorWidthNormalizer.normalizeSeparatorWidthsAndPadBlankLines(lines, separatorLineRegex)
    }
}

// ============================================================================
// SECTION 8: TABLE NORMALIZE HELPERS - PUBLIC API (was NormalizeHelpers.kt)
// ============================================================================

internal object TableNormalizeHelpers {
    fun isTableLine(line: String): Boolean = com.example.powerai.ui.screen.detail.isTableLine(line)
    
    fun isSeparatorLine(line: String): Boolean = com.example.powerai.ui.screen.detail.isSeparatorLine(line)
}
