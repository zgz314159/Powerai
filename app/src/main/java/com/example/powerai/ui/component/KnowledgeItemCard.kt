package com.example.powerai.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.ui.theme.HighlightYellow

@Composable
fun KnowledgeItemCard(
    item: KnowledgeItem,
    highlight: String = "",
    highlightStyleOverride: SpanStyle? = null,
    metaLine: String? = null,
    isSelected: Boolean = false,
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    previewMaxLines: Int = 4,
    modifier: Modifier = Modifier
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    OutlinedCard(
        modifier = clickableModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val highlightStyle = highlightStyleOverride ?: SpanStyle(
                background = HighlightYellow,
                color = Color.Unspecified
            )

            val titlePreview = remember(
                item.title,
                highlight,
                highlightStyle.background,
                highlightStyle.color
            ) {
                // Title can be very long (some KBs put the whole paragraph into title/jobTitle).
                // Use the same snippet-around-hit strategy so the keyword is visible even if it appears late.
                buildHighlightedPreview(
                    fullText = item.title,
                    keyword = highlight,
                    highlightStyle = highlightStyle,
                    maxChars = 140,
                    beforeChars = 8
                )
            }

            Text(
                text = titlePreview,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val meta = metaLine?.takeIf { it.isNotBlank() }
            if (meta != null) {
                Spacer(modifier = Modifier.padding(top = 2.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.padding(top = 6.dp))
            Row {
                Text(
                    text = "证据",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))

                val contentForPreview = remember(item.content) {
                    // Collapse newlines/whitespace for stable line wrapping.
                    // Without this, the hit may exist but be pushed beyond maxLines by hard line breaks.
                    item.content.replace(Regex("\\s+"), " ").trim()
                }
                val preview = remember(
                    contentForPreview,
                    highlight,
                    highlightStyle.background,
                    highlightStyle.color
                ) {
                    buildHighlightedPreview(
                        fullText = contentForPreview,
                        keyword = highlight,
                        highlightStyle = highlightStyle,
                        maxChars = 220,
                        beforeChars = 0
                    )
                }
                if (expanded) {
                    Text(text = preview)
                } else {
                    Text(
                        text = preview,
                        maxLines = previewMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onExpand != null) Modifier.clickable { onExpand() } else Modifier
                    )
                }
            }
        }
    }
}

private fun buildHighlightedPreview(
    fullText: String,
    keyword: String,
    highlightStyle: SpanStyle,
    maxChars: Int,
    beforeChars: Int = -1
): AnnotatedString {
    val raw = keyword.trim()
    if (maxChars <= 0) return AnnotatedString("")

    val candidates = listOf(
        raw,
        raw.filterNot { it.isWhitespace() },
        raw.filter { Character.isLetterOrDigit(it) }
    ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

    var matchIndex = -1
    var matchedKeyword = ""
    for (cand in candidates) {
        val idx = findMatchIndexIgnoringWhitespace(fullText, cand)
        if (idx >= 0) {
            matchIndex = idx
            matchedKeyword = cand
            break
        }
    }

    val (rawSnippet, snippetStart, snippetEnd) = if (matchIndex < 0) {
        val s = fullText.take(maxChars)
        Triple(s, 0, s.length)
    } else {
        val desiredBefore = if (beforeChars >= 0) beforeChars else (maxChars / 3).coerceAtLeast(24)
        var start = (matchIndex - desiredBefore).coerceAtLeast(0)
        var end = (start + maxChars).coerceAtMost(fullText.length)
        // If caller explicitly controls `beforeChars`, don't shift start backward.
        // Shifting backward makes hits near the end fall outside visible `maxLines`.
        if (beforeChars < 0 && end - start < maxChars && start > 0) {
            start = (end - maxChars).coerceAtLeast(0)
        }
        val s = fullText.substring(start, end)
        Triple(s, start, end)
    }

    val snippet = buildString {
        if (snippetStart > 0) append('…')
        append(rawSnippet)
        if (snippetEnd < fullText.length) append('…')
    }

    val kw = matchedKeyword.ifBlank { raw }
    if (kw.isBlank()) return AnnotatedString(snippet)
    return highlightAllWithWhitespaceFallback(
        text = snippet,
        keyword = kw,
        highlightStyle = highlightStyle
    )
}

private fun findMatchIndexIgnoringWhitespace(fullText: String, keyword: String): Int {
    val k = keyword.trim()
    if (k.isBlank()) return -1

    val direct = fullText.indexOf(k, ignoreCase = true)
    if (direct >= 0) return direct

    // Fallback: ignore any non-letter/digit separators in text (whitespace, punctuation, zero-width chars, etc.)
    // This aligns better with `TextSanitizer.normalizeForSearch` used by indexing/search.
    val keyCompact = k.filter { Character.isLetterOrDigit(it) }
    if (keyCompact.isBlank()) return -1
    return findFirstFuzzyMatchStart(text = fullText, keyCompact = keyCompact)
}

private fun findFirstFuzzyMatchStart(text: String, keyCompact: String): Int {
    if (keyCompact.isBlank()) return -1
    var i = 0
    while (i < text.length) {
        var t = i
        var j = 0
        while (t < text.length && j < keyCompact.length) {
            val tc = text[t]
            if (!Character.isLetterOrDigit(tc)) {
                t++
                continue
            }
            val kc = keyCompact[j]
            if (!tc.equals(kc, ignoreCase = true)) break
            t++
            j++
        }
        if (j == keyCompact.length) return i
        i++
    }
    return -1
}

private fun highlightAllWithWhitespaceFallback(
    text: String,
    keyword: String,
    highlightStyle: SpanStyle
): AnnotatedString {
    val k = keyword.trim()
    if (k.isBlank()) return AnnotatedString(text)

    // Fast path: exact substring highlight (all occurrences).
    run {
        val lowerText = text.lowercase()
        val lowerKey = k.lowercase()
        var start = 0
        var index = lowerText.indexOf(lowerKey, startIndex = 0)
        if (index < 0) return@run

        return buildAnnotatedString {
            while (index >= 0) {
                if (index > start) append(text.substring(start, index))
                withStyle(highlightStyle) { append(text.substring(index, index + k.length)) }
                start = index + k.length
                index = lowerText.indexOf(lowerKey, startIndex = start)
            }
            if (start < text.length) append(text.substring(start))
        }
    }

    // Fallback: fuzzy match that allows separators (whitespace/punctuation/zero-width chars) between keyword characters.
    val keyCompact = k.filter { Character.isLetterOrDigit(it) }
    if (keyCompact.isBlank()) return AnnotatedString(text)
    val spans = ArrayList<IntRange>()

    var i = 0
    while (i < text.length) {
        var t = i
        var j = 0
        while (t < text.length && j < keyCompact.length) {
            val tc = text[t]
            if (!Character.isLetterOrDigit(tc)) {
                t++
                continue
            }
            val kc = keyCompact[j]
            if (!tc.equals(kc, ignoreCase = true)) break
            t++
            j++
        }
        if (j == keyCompact.length) {
            // matched from i..(t-1), including whitespace inside
            spans.add(i until t)
            i = t
        } else {
            i++
        }
    }

    if (spans.isEmpty()) return AnnotatedString(text)

    // Merge overlapping/adjacent ranges to keep output stable.
    val merged = spans.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, r ->
        if (acc.isEmpty()) {
            acc.add(r)
        } else {
            val last = acc.last()
            if (r.first <= last.last + 1) {
                acc[acc.lastIndex] = last.first..maxOf(last.last, r.last)
            } else {
                acc.add(r)
            }
        }
        acc
    }

    return buildAnnotatedString {
        var cursor = 0
        for (range in merged) {
            val start = range.first.coerceIn(0, text.length)
            val endExclusive = (range.last + 1).coerceIn(0, text.length)
            if (start > cursor) append(text.substring(cursor, start))
            withStyle(highlightStyle) { append(text.substring(start, endExclusive)) }
            cursor = endExclusive
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
