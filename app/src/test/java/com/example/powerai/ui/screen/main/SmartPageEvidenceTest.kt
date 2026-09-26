package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for SmartPage's pure evidence helpers (visibility
 * change only): segmentation, citation indices, limits, labels plus empty,
 * missing and duplicate citation handling and boundary indices.
 */
class SmartPageEvidenceTest {
    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)

    private fun item(
        id: Long,
        source: String,
        title: String = "title-$id",
    ): KnowledgeItem =
        KnowledgeItem(
            id = id,
            title = title,
            content = "content-$id",
            source = source,
            category = "分类",
            keywords = emptyList(),
        )

    private fun entry(
        number: Int,
        id: Long,
        source: String,
    ): SmartEvidenceEntry = SmartEvidenceEntry(number = number, item = item(id, source))

    private val sourceA = "pdf:$shaA::手册A.pdf"
    private val sourceB = "pdf:$shaB::手册B.pdf"

    @Test
    fun `segments group consecutive entries by source file`() {
        val entries =
            listOf(
                entry(1, 1L, sourceA),
                entry(2, 2L, sourceA),
                entry(3, 3L, sourceB),
                entry(4, 4L, sourceA),
            )

        val segments = buildSmartEvidenceSegments(entries)

        // consecutive runs only: A, A | B | A
        assertEquals(3, segments.size)
        assertEquals(listOf(1, 2), segments[0].entries.map { it.number })
        assertEquals("手册A.pdf", segments[0].fileName)
        assertEquals(listOf(3), segments[1].entries.map { it.number })
        assertEquals("手册B.pdf", segments[1].fileName)
        assertEquals(listOf(4), segments[2].entries.map { it.number })
        assertEquals("seg-0-${"手册A.pdf".hashCode()}", segments[0].key)
    }

    @Test
    fun `empty evidence yields empty segments and indices`() {
        assertTrue(buildSmartEvidenceSegments(emptyList()).isEmpty())
        assertTrue(buildCitationTargetIndices(emptyList()).isEmpty())
        assertTrue(buildSmartAnchors(aiText = "", hasThinkingSection = false, evidenceSegments = emptyList()).isEmpty())
    }

    @Test
    fun `citation indices follow the lazy column layout`() {
        val segments =
            buildSmartEvidenceSegments(
                listOf(
                    entry(1, 1L, sourceA),
                    entry(2, 2L, sourceA),
                    entry(3, 3L, sourceB),
                ),
            )

        val indices = buildCitationTargetIndices(segments)

        // 0 answer, 1 evidence header, 2 file A row, 3/4 entries, 5 file B row, 6 entry
        assertEquals(mapOf(1 to 3, 2 to 4, 3 to 6), indices)
        assertEquals(7, (indices.values.maxOrNull() ?: 0) + 1)
    }

    @Test
    fun `missing citations are absent from the index`() {
        val segments = buildSmartEvidenceSegments(listOf(entry(1, 1L, sourceA)))
        val indices = buildCitationTargetIndices(segments)

        assertNull(indices[99])
        assertNull(indices[0])
        assertTrue(indices.containsKey(1))
    }

    @Test
    fun `duplicate citation numbers resolve to the last entry`() {
        val segments =
            buildSmartEvidenceSegments(
                listOf(
                    entry(1, 1L, sourceA),
                    entry(1, 2L, sourceA),
                ),
            )

        val indices = buildCitationTargetIndices(segments)

        // both entries share number 1; the later put wins
        assertEquals(4, indices[1])
        assertEquals(1, indices.size)
    }

    @Test
    fun `limit truncates entries across segments in order`() {
        val segments =
            buildSmartEvidenceSegments(
                listOf(
                    entry(1, 1L, sourceA),
                    entry(2, 2L, sourceA),
                    entry(3, 3L, sourceB),
                    entry(4, 4L, sourceB),
                    entry(5, 5L, sourceB),
                ),
            )

        val limited = limitSmartEvidenceSegments(segments, maxEntries = 4)
        assertEquals(listOf(2, 2), limited.map { it.entries.size })
        assertEquals(listOf(1, 2, 3, 4), limited.flatMap { it.entries }.map { it.number })
        assertEquals("手册B.pdf", limited[1].fileName)

        assertTrue(limitSmartEvidenceSegments(segments, maxEntries = 0).isEmpty())
        assertTrue(limitSmartEvidenceSegments(emptyList(), maxEntries = 4).isEmpty())

        val unlimited = limitSmartEvidenceSegments(segments, maxEntries = 10)
        assertEquals(5, unlimited.flatMap { it.entries }.size)
    }

    @Test
    fun `anchors cover answer thinking header and per file rows`() {
        val answerOnly = buildSmartAnchors(aiText = "答", hasThinkingSection = false, evidenceSegments = emptyList())
        assertEquals(1, answerOnly.size)
        assertEquals(0, answerOnly[0].itemIndex)
        assertEquals("回答", answerOnly[0].label)
        assertEquals("智能回答", answerOnly[0].fullLabel)

        val thinkingOnly = buildSmartAnchors(aiText = "", hasThinkingSection = true, evidenceSegments = emptyList())
        assertEquals("思", thinkingOnly[0].label)
        assertEquals("思考中", thinkingOnly[0].fullLabel)

        val segments =
            buildSmartEvidenceSegments(
                listOf(
                    entry(1, 1L, sourceA),
                    entry(2, 2L, sourceA),
                    entry(3, 3L, sourceB),
                ),
            )
        val anchors = buildSmartAnchors(aiText = "答", hasThinkingSection = false, evidenceSegments = segments)
        assertEquals(listOf(0, 1, 2, 5), anchors.map { it.itemIndex })
        assertEquals("证据", anchors[1].label)
        assertEquals("参考资", anchors[1].fullLabel)
        assertEquals("手册A.pdf", anchors[2].fullLabel)
        assertEquals("手册B.pdf", anchors[3].fullLabel)
        assertEquals("手册B.pdf", anchors[3].label)
    }

    @Test
    fun `bubble label formats answer header and per file hits`() {
        val segments =
            buildSmartEvidenceSegments(
                listOf(
                    entry(1, 1L, sourceA),
                    entry(2, 2L, sourceA),
                    entry(3, 3L, sourceB),
                ),
            )
        val anchors = buildSmartAnchors(aiText = "答", hasThinkingSection = false, evidenceSegments = segments)
        val fileAnchor = anchors.first { it.fullLabel == "手册A.pdf" }

        assertEquals("5", buildSmartBubbleLabel(4, null, anchors))
        assertEquals("智能回答", buildSmartBubbleLabel(0, anchors[0], anchors))
        assertEquals("参考资", buildSmartBubbleLabel(1, anchors[1], anchors))
        assertEquals("手册A.pdf", buildSmartBubbleLabel(fileAnchor.itemIndex, fileAnchor, anchors))
        assertEquals("手册A.pdf · 1条证", buildSmartBubbleLabel(fileAnchor.itemIndex + 1, fileAnchor, anchors))
        // at or past the next file anchor the label stays on the file name
        assertEquals("手册A.pdf", buildSmartBubbleLabel(fileAnchor.itemIndex + 3, fileAnchor, anchors))
    }

    @Test
    fun `file names resolve from pdf refs with safe fallbacks`() {
        assertEquals("手册A.pdf", smartEvidenceFileName(item(1, sourceA)))
        assertEquals("plain-source.pdf", smartEvidenceFileName(item(2, source = "plain-source.pdf")))
        assertEquals("参考资", smartEvidenceFileName(item(3, source = "")))

        assertEquals("短名", smartShortLabel("短名"))
        assertEquals("参考资", smartShortLabel(""))
        val long = "一个明显超过十四个字符限制的文件名称"
        assertEquals(long.take(14) + "...", smartShortLabel(long))
    }
}
