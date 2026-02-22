package com.example.powerai.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.powerai.ui.theme.HighlightYellow

internal fun highlightAll(text: String, keyword: String): AnnotatedString {
    val k = keyword.trim()
    if (k.isBlank()) return AnnotatedString(text)

    val lowerText = text.lowercase()
    val lowerKey = k.lowercase()

    var start = 0
    var index = lowerText.indexOf(lowerKey, startIndex = 0)

    if (index < 0) return AnnotatedString(text)

    return buildAnnotatedString {
        while (index >= 0) {
            if (index > start) {
                append(text.substring(start, index))
            }
            withStyle(
                SpanStyle(
                    background = HighlightYellow,
                    color = Color.Unspecified
                )
            ) {
                append(text.substring(index, index + k.length))
            }
            start = index + k.length
            index = lowerText.indexOf(lowerKey, startIndex = start)
        }
        if (start < text.length) {
            append(text.substring(start))
        }
    }
}
