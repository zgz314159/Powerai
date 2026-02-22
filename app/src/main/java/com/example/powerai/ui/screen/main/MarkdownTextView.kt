@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.powerai.data.importer.MarkdownTableNormalizer
import com.example.powerai.util.MarkwonHelper

private fun normalizeMathMarkdown(input: String): String {
    if (input.isBlank()) return input
    // Markwon LaTeX extension supports blocks and inline with $$...$$ delimiters.
    // Convert common TeX delimiters from model outputs for better compatibility.
    val blockNormalized = input.replace(
        Regex("\\\\\\[(.*?)\\\\\\]", setOf(RegexOption.DOT_MATCHES_ALL))
    ) { m ->
        "$$\n${m.groupValues[1].trim()}\n$$"
    }
    val inlineNormalized = blockNormalized.replace(
        Regex("\\\\\\((.*?)\\\\\\)", setOf(RegexOption.DOT_MATCHES_ALL))
    ) { m ->
        "$$${m.groupValues[1].trim()}$$"
    }

    // Auto-wrap plain TeX formula lines (e.g. "X_C = \\frac{1}{2\\pi f C}")
    // so they can be rendered by Markwon LaTeX plugin.
    val texCmd = Regex("\\\\(frac|sqrt|sum|int|pi|alpha|beta|gamma|Delta|theta|sin|cos|tan|cdot|times|left|right|mathrm|text)")
    val lines = inlineNormalized.lines()
    return lines.joinToString("\n") { line ->
        val trimmed = line.trim()
        val alreadyMath = trimmed.contains("$$") || trimmed.startsWith("$") || trimmed.endsWith("$")
        if (!alreadyMath && texCmd.containsMatchIn(trimmed) && trimmed.length <= 160) {
            "$$$trimmed$$"
        } else {
            line
        }
    }
}

private fun restoreCollapsedMarkdownTables(input: String): String {
    if (input.isBlank()) return input
    val lines = input.lines()
    return lines.joinToString("\n") { line ->
        val trimmed = line.trim()
        val looksCollapsedTable = trimmed.contains("||") &&
            trimmed.contains(":--") &&
            trimmed.count { it == '|' } >= 8 &&
            !trimmed.contains("\n")

        if (!looksCollapsedTable) {
            line
        } else {
            trimmed
                .split("||")
                .mapNotNull { seg ->
                    val s = seg.trim()
                    if (s.isBlank()) {
                        null
                    } else {
                        val start = if (s.startsWith("|")) s else "| $s"
                        if (start.endsWith("|")) start else "$start |"
                    }
                }
                .joinToString("\n")
        }
    }
}

private fun recoverAccidentalMarkdownCodeBlocks(input: String): String {
    if (input.isBlank()) return input

    fun looksLikeMarkdownLine(s: String): Boolean {
        val t = s.trim()
        if (t.isBlank()) return true
        val bulletLike = t.startsWith("* ") || t.startsWith("- ") || t.startsWith("+ ")
        val orderedLike = Regex("^\\d+\\.\\s+").containsMatchIn(t)
        val headingLike = t.startsWith("#")
        val quoteLike = t.startsWith(">")
        val hasMarkdownToken = t.contains("**") || t.contains("$$") || t.contains("|")
        return (bulletLike || orderedLike || headingLike || quoteLike) && hasMarkdownToken
    }

    val lines = input.lines()
    val out = ArrayList<String>(lines.size)
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trim() != "```") {
            out.add(line)
            i++
            continue
        }

        var j = i + 1
        while (j < lines.size && lines[j].trim() != "```") {
            j++
        }
        if (j >= lines.size) {
            out.add(line)
            i++
            continue
        }

        val inner = lines.subList(i + 1, j)
        val unwrap = inner.isNotEmpty() && inner.all { looksLikeMarkdownLine(it) }
        if (unwrap) {
            out.addAll(inner)
        } else {
            out.add(line)
            out.addAll(inner)
            out.add(lines[j])
        }
        i = j + 1
    }

    return out.joinToString("\n")
}

private fun recoverAccidentalIndentedMarkdown(input: String): String {
    if (input.isBlank()) return input
    val indentedMarkdown = Regex("^(\\s{4,})([*+-]\\s+.+|\\d+\\.\\s+.+|>\\s+.+|#{1,6}\\s+.+)$")
    return input.lines().joinToString("\n") { line ->
        val m = indentedMarkdown.find(line)
        if (m != null) {
            val content = m.groupValues[2]
            val shouldUnindent = content.contains("**") || content.contains("$$") || content.contains("|")
            if (shouldUnindent) content else line
        } else {
            line
        }
    }
}

@Composable
internal fun MarkdownTextView(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val markwonWithTables = remember(context) { MarkwonHelper.create(context) }
    val markwonNoTables = remember(context) { MarkwonHelper.createNoTables(context) }
    val normalized = remember(markdown) {
        val recoveredCodeBlocks = recoverAccidentalMarkdownCodeBlocks(markdown)
        val recoveredIndent = recoverAccidentalIndentedMarkdown(recoveredCodeBlocks)
        val recoveredTables = restoreCollapsedMarkdownTables(recoveredIndent)
        val uiTableNormalized = MarkdownTableNormalizer.normalizeMarkdownTablesForUi(recoveredTables)
        normalizeMathMarkdown(uiTableNormalized)
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                movementMethod = LinkMovementMethod.getInstance()
                setHorizontallyScrolling(false)
                isSingleLine = false
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            tv.setHorizontallyScrolling(false)
            try {
                markwonWithTables.setMarkdown(tv, normalized)
            } catch (_: Throwable) {
                markwonNoTables.setMarkdown(tv, normalized)
            }
        }
    )
}
