package com.example.powerai.ui.blocks

import com.example.powerai.core.model.FigureNodeBlock
import com.example.powerai.core.model.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocksParserTest {

    @Test
    fun `parseBlocks handles V2 schema with pages`() {
        val json = """
            {
              "document_id": "test_v2",
              "schema_version": "2.0",
              "pages": [
                {
                  "page_number": 1,
                  "width": 600,
                  "height": 800,
                  "blocks": [
                    {
                      "id": "p1_b1",
                      "type": "text",
                      "text": "Hello V2",
                      "bbox": {"x": 10, "y": 10, "w": 100, "h": 20},
                      "page": 1,
                      "reading_order": 0
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val blocks = BlocksParser.parseBlocks(json).orEmpty()
        assertEquals(1, blocks.size)

        val text = blocks[0] as TextBlock
        assertEquals("Hello V2", text.text)
        assertEquals(1, text.pageNumber)

        val bbox = text.boundingBox
        assertNotNull(bbox)
        assertTrue("bbox should retain x coordinate: $bbox", bbox!!.contains("\"x\":10"))
    }

    @Test
    fun `parseBlocks maps figure type to FigureNodeBlock`() {
        val json = """
            {
              "blocks": [
                {
                  "id": "fig_1",
                  "type": "figure",
                  "caption": "图1-1-1 电调监控网络运行图",
                  "images": [
                    "file:///android_asset/kb/铁路/专业知识/铁路电力/截图/visual_p5_1.png"
                  ],
                  "imageUri": "file:///android_asset/kb/铁路/专业知识/铁路电力/截图/visual_p5_1.png",
                  "bbox": {"x": 5, "y": 6, "w": 200, "h": 120},
                  "pageNumber": 3
                }
              ]
            }
        """.trimIndent()

        val blocks = BlocksParser.parseBlocks(json).orEmpty()
        assertEquals(1, blocks.size)

        val figure = blocks[0] as FigureNodeBlock
        assertEquals("fig_1", figure.id)
        assertEquals("图1-1-1 电调监控网络运行图", figure.caption)
        assertEquals(
            listOf("file:///android_asset/kb/铁路/专业知识/铁路电力/截图/visual_p5_1.png"),
            figure.images
        )
        assertEquals(
            "file:///android_asset/kb/铁路/专业知识/铁路电力/截图/visual_p5_1.png",
            figure.imageUri
        )
        assertEquals(3, figure.pageNumber)
        assertNotNull(figure.boundingBox)
        assertTrue(figure.boundingBox!!.contains("\"x\":5"))
    }

    @Test
    fun `parseBlocks keeps code blocks regardless of semanticRole`() {
        val json = """
            {
              "blocks": [
                {"type": "code", "code": "正文段落。", "semanticRole": "body"},
                {"type": "code", "code": "箱变", "semanticRole": "figure_callout"}
              ]
            }
        """.trimIndent()

        val blocks = BlocksParser.parseBlocks(json).orEmpty()
        assertEquals(2, blocks.size)
        // Current parser does not drop code blocks based on semanticRole=figure_callout.
        assertTrue(blocks.all { it is com.example.powerai.core.model.CodeBlock })
        assertEquals("正文段落。", (blocks[0] as com.example.powerai.core.model.CodeBlock).code)
        assertEquals("箱变", (blocks[1] as com.example.powerai.core.model.CodeBlock).code)
    }
}
