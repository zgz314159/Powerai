package com.example.powerai.data.importer

internal object PdfTextLineHeuristics {

    fun cleanLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return emptyList()

        val normalized = lines
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }

        val counts = normalized.groupingBy { it.lowercase() }.eachCount()
        val repeatedCandidates = counts.filter { (_, count) -> count >= 3 }.keys

        val result = ArrayList<String>(normalized.size)
        for (line in normalized) {
            if (line.isBlank()) continue
            if (isPageNumberLine(line)) continue
            if (line.lowercase() in repeatedCandidates && isHeaderLike(line)) continue
            result.add(line)
        }
        return result
    }

    fun mergeContinuationLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val out = ArrayList<String>(lines.size)
        var current: StringBuilder? = null

        fun flush() {
            val s = current?.toString()?.trim().orEmpty()
            if (s.isNotBlank()) out.add(s)
            current = null
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.isBlank()) {
                flush()
                continue
            }

            val isLikelyRowStart = line.matches(Regex("^\\d{1,4}([、\\.)]|\\s+).*")) ||
                line.contains(Regex("\\b\\d{4}[A-Z]\\d{2}\\b"))

            if (current == null || isLikelyRowStart) {
                flush()
                current = StringBuilder(line)
            } else {
                val prev = current!!
                val needsSpace = prev.isNotEmpty() && !prev.endsWith('-')
                if (needsSpace) prev.append(' ')
                prev.append(line)
            }
        }

        flush()
        return out
    }

    private fun isPageNumberLine(line: String): Boolean {
        return line.matches(Regex("""^\\s*(第\\s*\\d+\\s*页|\\d+)\\s*$"""))
    }

    private fun isHeaderLike(line: String): Boolean {
        if (line.length > 60) return false
        val headerKeywords = listOf("岗位", "招聘", "公告", "事业单位", "考试", "名录", "表", "市", "年")
        return headerKeywords.any { line.contains(it) }
    }
}
