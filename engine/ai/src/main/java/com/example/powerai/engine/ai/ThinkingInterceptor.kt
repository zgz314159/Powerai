package com.example.powerai.engine.ai

object ThinkingInterceptor {
    private val THINK_RE = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
    private val RESIDUAL_THINK_TAG_RE = Regex("</?think>", RegexOption.IGNORE_CASE)

    data class VisibleChunkResult(
        val text: String,
        val inThinkBlock: Boolean
    )

    data class StreamParseResult(
        val visibleText: String,
        val completedThinkingSegments: List<String>,
        val inThinkBlock: Boolean,
        val pendingThinkingText: String,
        val pendingTagFragment: String
    )

    /** 从模型输出中提取所<think>...</think> 段落（不包含标签）*/
    fun extractThinkingSegments(text: String): List<String> {
        return THINK_RE.findAll(text).mapNotNull { it.groups[1]?.value?.trim() }.toList()
    }

    /** 去掉完整<think>...</think> 思维块，保留最终可见答案*/
    fun stripThinkingSegments(text: String): String {
        if (containsUnclosedThink(text)) {
            return ""
        }

        val stripped = RESIDUAL_THINK_TAG_RE.replace(THINK_RE.replace(text, ""), "").trim()
        if (stripped.isNotBlank()) return stripped

        if (!text.contains("<think>", ignoreCase = true)) return stripped

        return ""
    }

    fun parseStreamingChunk(
        text: String,
        inThinkBlock: Boolean,
        pendingThinkingText: String,
        pendingTagFragment: String = ""
    ): StreamParseResult {
        val mergedText = pendingTagFragment + text
        val preservedSuffix = findTrailingPartialTagFragment(mergedText)
        val parseableText = if (preservedSuffix.isEmpty()) {
            mergedText
        } else {
            mergedText.dropLast(preservedSuffix.length)
        }

        val visible = StringBuilder()
        val currentThinking = StringBuilder(pendingThinkingText)
        val completedSegments = mutableListOf<String>()
        var cursor = 0
        var insideThink = inThinkBlock

        while (cursor < parseableText.length) {
            if (insideThink) {
                val closeIndex = parseableText.indexOf("</think>", startIndex = cursor)
                if (closeIndex < 0) {
                    currentThinking.append(parseableText.substring(cursor))
                    cursor = parseableText.length
                } else {
                    currentThinking.append(parseableText.substring(cursor, closeIndex))
                    val segment = currentThinking.toString().trim()
                    if (segment.isNotEmpty()) {
                        completedSegments += segment
                    }
                    currentThinking.clear()
                    cursor = closeIndex + "</think>".length
                    insideThink = false
                }
            } else {
                val openIndex = parseableText.indexOf("<think>", startIndex = cursor)
                if (openIndex < 0) {
                    visible.append(parseableText.substring(cursor))
                    cursor = parseableText.length
                } else {
                    visible.append(parseableText.substring(cursor, openIndex))
                    cursor = openIndex + "<think>".length
                    insideThink = true
                }
            }
        }

        return StreamParseResult(
            visibleText = visible.toString(),
            completedThinkingSegments = completedSegments,
            inThinkBlock = insideThink,
            pendingThinkingText = currentThinking.toString(),
            pendingTagFragment = preservedSuffix
        )
    }

    /**
     * 流式场景下剥离思维块内容，避免结果区把思维链也显示成正文
     */
    fun extractVisibleAnswerChunk(text: String, inThinkBlock: Boolean): VisibleChunkResult {
        val parsed = parseStreamingChunk(
            text = text,
            inThinkBlock = inThinkBlock,
            pendingThinkingText = "",
            pendingTagFragment = ""
        )

        return VisibleChunkResult(
            text = parsed.visibleText,
            inThinkBlock = parsed.inThinkBlock
        )
    }

    private fun findTrailingPartialTagFragment(text: String): String {
        val candidates = listOf("<think>", "</think>")
        for (tag in candidates) {
            for (length in (tag.length - 1) downTo 1) {
                val suffix = text.takeLast(length)
                if (tag.startsWith(suffix)) {
                    return suffix
                }
            }
        }
        return ""
    }

    private fun containsUnclosedThink(text: String): Boolean {
        val hasOpen = text.contains("<think>", ignoreCase = true)
        val hasClose = text.contains("</think>", ignoreCase = true)
        return hasOpen && !hasClose
    }

}
