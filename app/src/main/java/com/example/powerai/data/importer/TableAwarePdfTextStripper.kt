package com.example.powerai.data.importer

import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs

internal class TableAwarePdfTextStripper : PDFTextStripper() {
    private data class LineBucket(
        var y: Float,
        val sb: StringBuilder,
        var lastXEnd: Float,
        var minX: Float,
        var maxX: Float,
        var minY: Float,
        var maxY: Float
    )

    private val buckets = ArrayList<LineBucket>()
    private val lineMergeTolerance = 2.5f
    private val columnGapThreshold = 10f

    init {
        sortByPosition = true
        setAverageCharTolerance(0.3f)
        setSpacingTolerance(0.35f)
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return

        val ys = textPositions.map { it.yDirAdj }
        val xs = textPositions.map { it.xDirAdj }
        val avgY = ys.average().toFloat()
        val xStart = xs.minOrNull() ?: textPositions.first().xDirAdj
        val last = textPositions.maxByOrNull { it.xDirAdj } ?: textPositions.first()
        val xEnd = last.xDirAdj + last.widthDirAdj
        val yMin = ys.minOrNull() ?: avgY
        val yMax = ys.maxOrNull() ?: avgY

        val targetIndex = findTargetLineIndex(avgY)
        if (targetIndex == -1) {
            buckets.add(LineBucket(y = avgY, sb = StringBuilder(cleaned), lastXEnd = xEnd, minX = xStart, maxX = xEnd, minY = yMin, maxY = yMax))
        } else {
            val bucket = buckets[targetIndex]
            val gap = xStart - bucket.lastXEnd
            if (bucket.sb.isNotEmpty()) {
                val lastChar = bucket.sb.lastOrNull()
                val firstChar = cleaned.firstOrNull()
                val shouldJoinNoSpace = gap <= columnGapThreshold &&
                    lastChar != null && firstChar != null &&
                    ((isCjk(lastChar) && isCjk(firstChar)) || (isAlphaNum(lastChar) && isAlphaNum(firstChar)))
                if (!shouldJoinNoSpace) {
                    bucket.sb.append(if (gap > columnGapThreshold) "  " else " ")
                }
            }
            bucket.sb.append(cleaned)
            bucket.lastXEnd = maxOf(bucket.lastXEnd, xEnd)
            bucket.y = (bucket.y + avgY) / 2f
            bucket.minX = minOf(bucket.minX, xStart)
            bucket.maxX = maxOf(bucket.maxX, xEnd)
            bucket.minY = minOf(bucket.minY, yMin)
            bucket.maxY = maxOf(bucket.maxY, yMax)
        }
    }

    private fun isCjk(c: Char): Boolean = c.code in 0x4E00..0x9FFF
    private fun isAlphaNum(c: Char): Boolean = c.isLetterOrDigit()

    override fun writeLineSeparator() {
        // no-op
    }

    fun getLinesSorted(): List<String> {
        return buckets
            .sortedBy { it.y }
            .map { it.sb.toString().trim() }
            .filter { it.isNotBlank() }
    }

    fun getLinesWithBboxes(): List<Pair<String, String>> {
        return buckets
            .sortedBy { it.y }
            .mapNotNull { b ->
                val text = b.sb.toString().trim()
                if (text.isBlank()) return@mapNotNull null
                val bboxJson = "{\"xMin\":${b.minX},\"yMin\":${b.minY},\"xMax\":${b.maxX},\"yMax\":${b.maxY}}"
                Pair(text, bboxJson)
            }
    }

    private fun findTargetLineIndex(y: Float): Int {
        if (buckets.isEmpty()) return -1
        var bestIndex = -1
        var bestDiff = Float.MAX_VALUE
        for (i in buckets.indices) {
            val diff = abs(buckets[i].y - y)
            if (diff <= lineMergeTolerance && diff < bestDiff) {
                bestDiff = diff
                bestIndex = i
            }
        }
        return bestIndex
    }
}
