package com.example.powerai.ui.screen.main

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the structured text builder extracted from
 * ResponseBodyComponents: heading labels, list markers and plain lines keep
 * their original emphasis behavior.
 */
class ResponseStructuredTextTest {
    private val headingColor = androidx.compose.ui.graphics.Color.Black

    private fun hasSemiBold(
        structured: AnnotatedString,
        start: Int,
        end: Int,
    ): Boolean =
        structured.spanStyles.any { span ->
            span.start == start && span.end == end && span.item.fontWeight == FontWeight.SemiBold
        }

    @Test
    fun `heading line emphasizes the label and splits the remainder`() {
        val structured =
            buildStructuredAnnotatedText(
                source = AnnotatedString("结论：内容如下"),
                headingColor = headingColor,
            )

        assertEquals("结论\n内容如下", structured.text)
        assertTrue(hasSemiBold(structured, 0, 2))
    }

    @Test
    fun `heading without separator keeps label emphasis only`() {
        val structured =
            buildStructuredAnnotatedText(
                source = AnnotatedString("注意安全事项"),
                headingColor = headingColor,
            )

        assertEquals("注意", structured.text.substringBefore("\n"))
        assertTrue(hasSemiBold(structured, 0, 2))
    }

    @Test
    fun `list marker line emphasizes the marker and keeps the item text`() {
        val structured =
            buildStructuredAnnotatedText(
                source = AnnotatedString("1. 第一项内容"),
                headingColor = headingColor,
            )

        assertEquals("1. 第一项内容", structured.text)
        assertTrue(hasSemiBold(structured, 0, 2))
    }

    @Test
    fun `plain line stays unchanged without spans`() {
        val structured =
            buildStructuredAnnotatedText(
                source = AnnotatedString("普通的正文内容"),
                headingColor = headingColor,
            )

        assertEquals("普通的正文内容", structured.text)
        assertTrue(structured.spanStyles.isEmpty())
    }

    @Test
    fun `blank source returns the original annotated string`() {
        val source = AnnotatedString("")
        val structured = buildStructuredAnnotatedText(source = source, headingColor = headingColor)

        assertEquals(source, structured)
    }
}
