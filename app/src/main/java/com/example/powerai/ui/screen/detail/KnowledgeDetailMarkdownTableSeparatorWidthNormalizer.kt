package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMarkdownTableSeparatorWidthNormalizer {
    fun normalizeSeparatorWidthsAndPadBlankLines(
        lines: MutableList<String>,
        separatorLineRegex: Regex
    ) {
        var i = 0
        while (i < lines.size) {
            val t = lines[i].trim()
            if (separatorLineRegex.matches(t)) {
                var maxPipes = KnowledgeDetailMarkdownTableSeparatorHelpers.pipeCount(t)

                var j = i - 1
                while (j >= 0) {
                    val lt = lines[j].trim()
                    if (lt.isBlank()) break
                    if (!lt.startsWith("|")) break
                    if (separatorLineRegex.matches(lt)) break
                    maxPipes = maxOf(
                        maxPipes,
                        KnowledgeDetailMarkdownTableSeparatorHelpers.pipeCount(
                            KnowledgeDetailMarkdownTableSeparatorHelpers.normalizeRowPipes(lt)
                        )
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
                        KnowledgeDetailMarkdownTableSeparatorHelpers.pipeCount(
                            KnowledgeDetailMarkdownTableSeparatorHelpers.normalizeRowPipes(lt)
                        )
                    )
                    k++
                }

                val columns = (maxPipes - 1).coerceAtLeast(1)
                lines[i] = KnowledgeDetailMarkdownTableSeparatorHelpers.buildSeparatorRow(columns)

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
