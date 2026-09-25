package com.example.powerai.ui.component

import com.example.powerai.core.model.util.TextSanitizer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Builds a highlighted preview snippet from full text containing a keyword.
 *
 * @param fullText The complete text to extract a preview from
 * @param keyword The keyword to highlight
 * @param highlightStyle The style to apply to highlighted text
 * @param maxChars Maximum characters in the preview
 * @param beforeChars Characters to show before the match (default: auto-calculated)
 * @return AnnotatedString with highlighted keyword and ellipsis markers
 */
internal fun buildHighlightedPreview(
    fullText: String,
    keyword: String,
    highlightStyle: SpanStyle,
    maxChars: Int,
    beforeChars: Int = -1
): AnnotatedString {
    val raw = keyword.trim()
    if (maxChars <= 0) return AnnotatedString("")

    val (matchIndex, matchedKeyword) = findBestMatchingKeyword(fullText, raw)
    val (rawSnippet, snippetStart, snippetEnd) = extractSnippetWithContext(fullText, matchIndex, maxChars, beforeChars)

    val snippet = buildString {
        if (snippetStart > 0) append('…')
        append(rawSnippet)
        if (snippetEnd < fullText.length) append('…')
    }

    val kw = matchedKeyword.ifBlank { raw }
    if (kw.isBlank()) return AnnotatedString(snippet)
    return highlightAllWithWhitespaceFallback(snippet, kw, highlightStyle)
}

private data class KeywordMatch(val index: Int, val keyword: String)

private fun findBestMatchingKeyword(fullText: String, raw: String): KeywordMatch {
    val candidates = listOf(
        raw,
        raw.filterNot { it.isWhitespace() },
        raw.filter { Character.isLetterOrDigit(it) }
    ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

    for (cand in candidates) {
        val idx = findMatchIndexIgnoringWhitespace(fullText, cand)
        if (idx >= 0) return KeywordMatch(idx, cand)
    }
    return KeywordMatch(-1, "")
}

private data class SnippetRange(val snippet: String, val start: Int, val end: Int)

private fun extractSnippetWithContext(
    fullText: String,
    matchIndex: Int,
    maxChars: Int,
    beforeChars: Int
): SnippetRange {
    if (matchIndex < 0) {
        val s = fullText.take(maxChars)
        return SnippetRange(s, 0, s.length)
    }

    val desiredBefore = if (beforeChars >= 0) beforeChars else (maxChars / 3).coerceAtLeast(24)
    var start = (matchIndex - desiredBefore).coerceAtLeast(0)
    var end = (start + maxChars).coerceAtMost(fullText.length)
    if (beforeChars < 0 && end - start < maxChars && start > 0) {
        start = (end - maxChars).coerceAtLeast(0)
    }
    val s = fullText.substring(start, end)
    return SnippetRange(s, start, end)
}

/**
 * Finds the first match index of a keyword in text, ignoring whitespace differences.
 *
 * First attempts exact substring match, then falls back to fuzzy matching
 * by comparing only letter/digit characters.
 *
 * @param fullText The text to search in
 * @param keyword The keyword to find
 * @return Index of first match, or -1 if not found
 */
internal fun findMatchIndexIgnoringWhitespace(fullText: String, keyword: String): Int {
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

/**
 * Finds the first fuzzy match start position by comparing only letter/digit characters.
 *
 * Ignores whitespace, punctuation, and other separators when matching.
 *
 * @param text The text to search in
 * @param keyCompact The keyword with only letter/digit characters
 * @return Start index of first match, or -1 if not found
 */
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

/**
 * Highlights all occurrences of a keyword in text with whitespace fallback.
 *
 * Fast path: exact substring matching for all occurrences.
 * Fallback: fuzzy matching that allows separators between keyword characters.
 * Automatically merges overlapping/adjacent highlight ranges.
 *
 * @param text The text to process
 * @param keyword The keyword to highlight
 * @param highlightStyle The style to apply to matched text
 * @return AnnotatedString with all matches highlighted
 */
internal fun highlightAllWithWhitespaceFallback(
    text: String,
    keyword: String,
    highlightStyle: SpanStyle
): AnnotatedString {
    val k = keyword.trim()
    if (k.isBlank()) return AnnotatedString(text)

    // Fast path: exact substring highlight
    val exactResult = exactSubstringHighlight(text, k, highlightStyle)
    if (exactResult != null) return exactResult

    // Fallback: fuzzy match allowing separators
    val keyCompact = k.filter { Character.isLetterOrDigit(it) }
    if (keyCompact.isBlank()) return AnnotatedString(text)

    val spans = collectFuzzyMatchRanges(text, keyCompact)
    if (spans.isEmpty()) return AnnotatedString(text)

    val merged = mergeRanges(spans)
    return applyHighlightRanges(text, merged, highlightStyle)
}

private fun exactSubstringHighlight(text: String, keyword: String, highlightStyle: SpanStyle): AnnotatedString? {
    val lowerText = text.lowercase()
    val lowerKey = keyword.lowercase()
    val firstIndex = lowerText.indexOf(lowerKey)
    if (firstIndex < 0) return null

    return buildAnnotatedString {
        var start = 0
        var index = firstIndex
        while (index >= 0) {
            if (index > start) append(text.substring(start, index))
            withStyle(highlightStyle) { append(text.substring(index, index + keyword.length)) }
            start = index + keyword.length
            index = lowerText.indexOf(lowerKey, startIndex = start)
        }
        if (start < text.length) append(text.substring(start))
    }
}

private fun collectFuzzyMatchRanges(text: String, keyCompact: String): List<IntRange> {
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
            spans.add(i until t)
            i = t
        } else {
            i++
        }
    }
    return spans
}

private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
    return ranges.sortedBy { it.first }.fold(mutableListOf()) { acc, r ->
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
}

private fun applyHighlightRanges(text: String, ranges: List<IntRange>, highlightStyle: SpanStyle): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges) {
            val start = range.first.coerceIn(0, text.length)
            val endExclusive = (range.last + 1).coerceIn(0, text.length)
            if (start > cursor) append(text.substring(cursor, start))
            withStyle(highlightStyle) { append(text.substring(start, endExclusive)) }
            cursor = endExclusive
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
