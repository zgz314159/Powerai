package com.example.powerai.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden characterization tests for [SemanticRoleSearchTextBuilder].
 *
 * They pin role inference precedence, figure/table signal extraction, query
 * matching, fallback selection, empty/malformed input handling, normalization
 * and determinism of the real output (not just non-emptiness).
 */
class SemanticRoleSearchTextBuilderTest {
    @Test
    fun `analyze counts explicit roles and type mappings`() {
        val json =
            """
            {"blocks":[
              {"semanticRole":"heading","text":"标题"},
              {"semanticRole":"table_note","text":"注1"},
              {"semanticRole":"caption","text":"图注"},
              {"semanticRole":"artifact","text":"附录"},
              {"type":"table","cells":[["a"]]},
              {"type":"image","src":"file:///x.png"},
              {"type":"code","code":"val x = 1"}
            ]}
            """.trimIndent()

        val profile = SemanticRoleSearchTextBuilder.analyze(json)

        assertEquals(7, profile.totalBlocks)
        assertEquals(1, profile.headingCount)
        assertEquals(1, profile.bodyCount)
        assertEquals(1, profile.tableCount)
        assertEquals(1, profile.tableNoteCount)
        assertEquals(1, profile.captionCount)
        assertEquals(1, profile.artifactCount)
        assertEquals(1, profile.figureCount)
        assertEquals("heading", profile.dominantRole)
        assertFalse(profile.lowValueOnly)
    }

    @Test
    fun `analyze prefers explicit semanticRole over type mapping`() {
        val json =
            """
            {"blocks":[
              {"type":"table","semanticRole":"heading","text":"h"},
              {"type":"image","semanticRole":"body","src":"file:///x.png"}
            ]}
            """.trimIndent()

        val profile = SemanticRoleSearchTextBuilder.analyze(json)

        assertEquals(1, profile.headingCount)
        assertEquals(0, profile.tableCount)
        assertEquals(1, profile.bodyCount)
        assertEquals(0, profile.figureCount)
    }

    @Test
    fun `analyze maps imageUris and figureNodeId to figure_node`() {
        val json =
            """
            {"blocks":[
              {"imageUris":["file:///a.png"],"text":"fig"},
              {"figureNodeId":"fn-7","text":"node"},
              {"type":"table","cells":[["x"]]}
            ]}
            """.trimIndent()

        val profile = SemanticRoleSearchTextBuilder.analyze(json)

        assertEquals(2, profile.figureNodeCount)
        assertEquals(1, profile.tableCount)
        assertEquals(3, profile.totalBlocks)
        assertEquals("figure_node", profile.dominantRole)
        assertFalse(profile.lowValueOnly)
    }

    @Test
    fun `analyze counts root level figureNodes array`() {
        val json =
            """
            {"blocks":[{"type":"table","cells":[["x"]]}],
             "figureNodes":[
               {"label":"图1","caption":"主接线图"},
               {"label":"图2"}
             ]}
            """.trimIndent()

        val profile = SemanticRoleSearchTextBuilder.analyze(json)

        assertEquals(3, profile.totalBlocks)
        assertEquals(2, profile.figureNodeCount)
        assertEquals(1, profile.tableCount)
    }

    @Test
    fun `analyze marks low value only when no primary roles and no figure nodes`() {
        val lowValue =
            """
            {"blocks":[
              {"semanticRole":"caption","text":"图注"},
              {"semanticRole":"artifact","text":"附"}
            ]}
            """.trimIndent()
        assertTrue(SemanticRoleSearchTextBuilder.analyze(lowValue).lowValueOnly)

        val withBody = """{"blocks":[{"text":"正文内容"}]}"""
        assertFalse(SemanticRoleSearchTextBuilder.analyze(withBody).lowValueOnly)

        val withFigureNode = """{"blocks":[{"figureNodeId":"fn1","text":"图"}]}"""
        assertFalse(SemanticRoleSearchTextBuilder.analyze(withFigureNode).lowValueOnly)
    }

    @Test
    fun `analyze query matching reports label caption and canonical id`() {
        val json =
            """
            {"blocks":[],
             "figureNodes":[
               {"label":"图1 主接线",
                "caption":"主接线图说明",
                "canonicalFigureNodeId":"fn-42",
                "canonicalFigureNode":{"id":"fn-42","label":"图1 主接线"}},
               {"label":"图2 避雷器","caption":"避雷器说明"}
             ]}
            """.trimIndent()

        val profile = SemanticRoleSearchTextBuilder.analyze(json, query = "主接线")

        assertEquals(1, profile.figureLabelMatchCount)
        assertEquals(1, profile.figureCaptionMatchCount)
        assertEquals("图1 主接线", profile.matchedFigureLabel)
        // figure node text joins label and caption parts
        assertEquals("图1 主接线 主接线图说明", profile.matchedFigureCaption)
        assertEquals("fn-42", profile.matchedCanonicalFigureNodeId)
    }

    @Test
    fun `analyze query matching ignores whitespace differences`() {
        val json =
            """
            {"blocks":[],
             "figureNodes":[{"label":"主 接 线","caption":"说明"}]}
            """.trimIndent()

        val profile = SemanticRoleSearchTextBuilder.analyze(json, query = "主接线")

        assertEquals(1, profile.figureLabelMatchCount)
        assertEquals("主 接 线", profile.matchedFigureLabel)
    }

    @Test
    fun `analyze blank or unmatched query yields no signals`() {
        val json =
            """
            {"blocks":[],
             "figureNodes":[{"label":"图1 主接线","caption":"主接线图"}]}
            """.trimIndent()

        val blank = SemanticRoleSearchTextBuilder.analyze(json, query = " ")
        assertEquals(0, blank.figureLabelMatchCount)
        assertEquals(0, blank.figureCaptionMatchCount)
        assertEquals("", blank.matchedFigureLabel)

        val unmatched = SemanticRoleSearchTextBuilder.analyze(json, query = "完全无关的问题")
        assertEquals(0, unmatched.figureLabelMatchCount)
        assertEquals("", unmatched.matchedCanonicalFigureNodeId)
    }

    @Test
    fun `analyze falls back to plain text profile when blocks are missing`() {
        val withFallback = SemanticRoleSearchTextBuilder.analyze(null, fallbackPlainText = "正文")
        assertEquals(1, withFallback.totalBlocks)
        assertEquals(1, withFallback.bodyCount)
        assertEquals("body", withFallback.dominantRole)
        assertFalse(withFallback.lowValueOnly)

        val withoutFallback = SemanticRoleSearchTextBuilder.analyze("", fallbackPlainText = "")
        assertEquals(0, withoutFallback.totalBlocks)
        assertEquals(0, withoutFallback.bodyCount)
        assertEquals("", withoutFallback.dominantRole)
    }

    @Test
    fun `analyze tolerates malformed json and bare objects`() {
        // Gson lenient parsing turns a bare token into a primitive root, which
        // counts as a single body block; structurally invalid JSON yields null.
        assertEquals(1, SemanticRoleSearchTextBuilder.analyze("not-json").totalBlocks)
        assertEquals(0, SemanticRoleSearchTextBuilder.analyze("[[[[").totalBlocks)

        // A root object without a blocks array is treated as one body block.
        val bare = SemanticRoleSearchTextBuilder.analyze("""{"text":"solo"}""")
        assertEquals(1, bare.totalBlocks)
        assertEquals(1, bare.bodyCount)
        assertEquals("body", bare.dominantRole)
    }

    @Test
    fun `buildSearchPayload weights roles in deterministic repetition order`() {
        val json =
            """
            {"blocks":[
              {"semanticRole":"heading","text":"HEAD"},
              {"semanticRole":"body","text":"BODY"}
            ]}
            """.trimIndent()

        val payload =
            SemanticRoleSearchTextBuilder.buildSearchPayload(
                title = "",
                source = "",
                category = "",
                pageNumber = null,
                keywords = emptyList(),
                plainText = "",
                blocksJson = json,
            )

        // heading weight=3, body weight=2, joined by blank lines in role order
        assertEquals("HEAD\n\nHEAD\n\nHEAD\n\nBODY\n\nBODY", payload)
    }

    @Test
    fun `buildSearchPayload appends fallback when only zero weight blocks exist`() {
        val json = """{"blocks":[{"type":"image","src":"file:///x.png"}]}"""
        val fallback = "这是一段足够长的正文内容，用来验证零权重角色时的兜底拼接行为。"

        val payload =
            SemanticRoleSearchTextBuilder.buildSearchPayload(
                title = "",
                source = "",
                category = "",
                pageNumber = null,
                keywords = emptyList(),
                plainText = fallback,
                blocksJson = json,
            )

        assertTrue("fallback must be appended: $payload", payload.contains(fallback))
    }

    @Test
    fun `buildSearchPayload appends body-like fallback alongside low value roles`() {
        val json = """{"blocks":[{"semanticRole":"caption","text":"CAP"}]}"""
        val longFallback = "这段正文长度超过四十个字符，因此会被判定为像正文的兜底内容。"

        val payload =
            SemanticRoleSearchTextBuilder.buildSearchPayload(
                title = "",
                source = "",
                category = "",
                pageNumber = null,
                keywords = emptyList(),
                plainText = longFallback,
                blocksJson = json,
            )

        assertTrue(payload.contains("CAP"))
        assertTrue(payload.contains(longFallback))
    }

    @Test
    fun `buildNormalizedSearchContent normalizes punctuation and case`() {
        val normalized =
            SemanticRoleSearchTextBuilder.buildNormalizedSearchContent(
                title = "",
                source = "",
                category = "",
                pageNumber = null,
                keywords = emptyList(),
                plainText = "Hello, World! 你好",
                blocksJson = null,
            )

        assertFalse(normalized.contains(","))
        assertFalse(normalized.contains("!"))
        assertTrue(normalized.contains("hello world"))
        assertTrue(normalized.contains("你好"))
    }

    @Test
    fun `same input produces identical profile and payload`() {
        val json =
            """
            {"blocks":[
              {"semanticRole":"heading","text":"标题"},
              {"type":"table","cells":[["1","2"]]},
              {"figureNodeId":"fn9","text":"图9"}
            ],
             "figureNodes":[{"label":"图9 汇总","caption":"汇总图"}]}
            """.trimIndent()

        val profile1 = SemanticRoleSearchTextBuilder.analyze(json, query = "汇总")
        val profile2 = SemanticRoleSearchTextBuilder.analyze(json, query = "汇总")
        assertEquals(profile1, profile2)

        val payload1 =
            SemanticRoleSearchTextBuilder.buildSearchPayload(
                title = "T",
                source = "s.pdf",
                category = "c",
                pageNumber = 7,
                keywords = listOf("k"),
                plainText = "plain",
                blocksJson = json,
            )
        val payload2 =
            SemanticRoleSearchTextBuilder.buildSearchPayload(
                title = "T",
                source = "s.pdf",
                category = "c",
                pageNumber = 7,
                keywords = listOf("k"),
                plainText = "plain",
                blocksJson = json,
            )
        assertTrue(payload1.isNotBlank())
        assertEquals(payload1, payload2)
    }
}
