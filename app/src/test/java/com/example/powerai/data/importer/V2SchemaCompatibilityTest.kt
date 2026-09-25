package com.example.powerai.data.importer

import com.example.powerai.core.model.TextBlock
import com.example.powerai.ui.blocks.BlocksParser
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class V2SchemaCompatibilityTest {
    private val gson = Gson()

    @Test
    fun `parse V2 schema correctly extracts schemaVersion and metadata`() {
        val json = """{
            "fileMetadata": {
                "schemaVersion": "2.0",
                "fileId": "railway_v2",
                "fileName": "railway.pdf"
            },
            "entries": [
                {
                    "entryId": "e1",
                    "jobTitle": "Chapter 1",
                    "blocks": [
                        {
                            "type": "text",
                            "text": "Hello V2",
                            "bbox": [10, 10, 100, 20],
                            "pdfWidth": 595.0,
                            "pdfHeight": 841.0
                        }
                    ]
                }
            ]
        }""".trimIndent()

        val root = JsonParser.parseString(json)
        val (meta, entries) = JsonResourceParser.parseRoot(root, gson, null, null)

        assertEquals("2.0", meta.schemaVersion)
        assertEquals("railway_v2", meta.fileId)
        assertEquals(1, entries.size)

        val entry = entries[0]
        val parsedBlocks = BlocksParser.parseBlocks(entry.blocks.toString())
        assertNotNull(parsedBlocks)
        assertEquals(1, parsedBlocks!!.size)

        val block = parsedBlocks[0] as TextBlock
        assertEquals("Hello V2", block.text)
        assertEquals(595.0f, block.pdfWidth)
        assertEquals(841.0f, block.pdfHeight)

        // Bbox array is converted to string by boundingBoxAsStringOrNull
        assertTrue(block.boundingBox!!.contains("10"))
    }
}
