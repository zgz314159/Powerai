package com.example.powerai.ui.screen.main

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 纯文本展示（无注解）
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ResponsePlainTextContent(
    text: String,
    bodyStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val headingColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
    val structuredText =
        remember(text, headingColor) {
            buildStructuredAnnotatedText(
                source = AnnotatedString(text),
                headingColor = headingColor,
            )
        }
    Text(text = structuredText, style = bodyStyle, modifier = modifier)
}

private val structuralHeadingRegex = Regex("^(结论|建议|提示|说明|依据|注意|备注)[:：]?\\s*(.*)$")

// NOTE: the pre-split pattern contained an illegal char range (九-9) that made
// class initialization throw PatternSyntaxException; 0-9 restores the
// intended parenthesized list-marker set (Chinese numerals plus digits).
private val structuralListRegex = Regex("^(\\d+[.、]|[一二三四五六七八九十]+、|（[一二三四五六七八九0-9]+）|\\([一二三四五六七八九0-9]+\\))\\s*(.*)$")

internal fun buildStructuredAnnotatedText(
    source: AnnotatedString,
    headingColor: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    if (source.text.isBlank()) return source

    return buildAnnotatedString {
        var cursor = 0
        val text = source.text

        while (cursor < text.length) {
            val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
            val rawLine = text.substring(cursor, lineEnd)
            val lineSource = source.subSequence(cursor, lineEnd)

            appendStructuredLine(
                rawLine = rawLine,
                lineSource = lineSource,
                headingColor = headingColor,
            )

            if (lineEnd < text.length) {
                append("\n")
            }
            cursor = lineEnd + 1
        }
    }
}

@Suppress("ComplexCondition")
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendStructuredLine(
    rawLine: String,
    lineSource: AnnotatedString,
    headingColor: androidx.compose.ui.graphics.Color,
) {
    val headingMatch = structuralHeadingRegex.find(rawLine)
    if (headingMatch != null) {
        val label = headingMatch.groupValues[1]
        var remainderStart = label.length
        while (
            remainderStart < rawLine.length &&
            (rawLine[remainderStart] == ':' || rawLine[remainderStart] == '：' || rawLine[remainderStart].isWhitespace())
        ) {
            remainderStart++
        }
        withStyle(
            SpanStyle(
                color = headingColor,
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            append(label)
        }
        if (remainderStart < rawLine.length) {
            append("\n")
            append(lineSource.subSequence(remainderStart, lineSource.length))
        }
        return
    }

    val listMatch = structuralListRegex.find(rawLine)
    if (listMatch != null) {
        val marker = listMatch.groupValues[1]
        var remainderStart = marker.length
        while (remainderStart < rawLine.length && rawLine[remainderStart].isWhitespace()) {
            remainderStart++
        }
        withStyle(
            SpanStyle(
                color = headingColor,
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            append(marker)
        }
        if (remainderStart < rawLine.length) {
            append(" ")
            append(lineSource.subSequence(remainderStart, lineSource.length))
        }
        return
    }

    append(lineSource)
}
