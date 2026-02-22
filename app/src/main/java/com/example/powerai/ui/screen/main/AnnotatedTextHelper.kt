package com.example.powerai.ui.screen.main

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

internal fun buildCitedAndLinkedText(
    text: String,
    citationColor: androidx.compose.ui.graphics.Color,
    linkColor: androidx.compose.ui.graphics.Color
): AnnotatedString {
    val citationRegex = Regex("\\[(\\d{1,3})]")
    val urlRegex = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")

    data class Span(val start: Int, val endExclusive: Int, val type: String, val value: String)

    val spans = buildList {
        for (m in citationRegex.findAll(text)) {
            val number = m.groupValues.getOrNull(1).orEmpty()
            add(Span(m.range.first, m.range.last + 1, "citation", number))
        }
        for (m in urlRegex.findAll(text)) {
            val url = m.value
            add(Span(m.range.first, m.range.last + 1, "url", url))
        }
    }.sortedWith(compareBy<Span> { it.start }.thenByDescending { it.endExclusive })

    return buildAnnotatedString {
        var cursor = 0
        for (s in spans) {
            if (s.start < cursor) continue
            if (s.start > cursor) {
                append(text.substring(cursor, s.start))
            }

            when (s.type) {
                "citation" -> {
                    pushStringAnnotation(tag = "citation", annotation = s.value)
                    withStyle(
                        SpanStyle(
                            color = citationColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(text.substring(s.start, s.endExclusive))
                    }
                    pop()
                }
                "url" -> {
                    pushStringAnnotation(tag = "url", annotation = s.value)
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(text.substring(s.start, s.endExclusive))
                    }
                    pop()
                }
            }

            cursor = s.endExclusive
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
