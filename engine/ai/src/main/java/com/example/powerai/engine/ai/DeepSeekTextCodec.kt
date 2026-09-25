package com.example.powerai.engine.ai

import com.example.powerai.core.model.util.TextSanitizer
import java.nio.charset.Charset

object DeepSeekTextCodec {
    private val gb18030: Charset = Charset.forName("GB18030")
    private val windows1252: Charset = Charset.forName("Windows-1252")
    private val mojibakeMarkers = Regex("[鍙涓浠浣鐨勭殑锛銆闂棰樿緭鍑哄彉鍘嬪櫺鍥戒腑ÃÂâãåæçèéêëîïðñòóôõöøùúûüýþÿ¼½¾]")
    private val commonArtifactReplacements = listOf(
        "ã" to "。",
        "ã" to "、",
        "ï¼" to "，",
        "ï¼" to "：",
        "ï¼" to "；",
        "â" to "“",
        "â" to "”",
        "â" to "‘",
        "â" to "’"
    )

    fun normalizePrompt(prompt: String): String {
        val sanitized = TextSanitizer.sanitizeText(prompt)
        return maybeRepairMojibake(sanitized)
    }

    fun normalizeChunk(chunk: String): String {
        val sanitized = sanitizeChunkPreservingWhitespace(chunk)
        return maybeRepairMojibake(sanitized)
    }

    fun likelyContainsMojibake(text: String): Boolean {
        return mojibakeMarkers.containsMatchIn(text)
    }

    fun normalizeFinalAnswer(text: String): String {
        if (text.isBlank()) return text
        var normalized = ThinkingInterceptor.stripThinkingSegments(text)
        for ((bad, good) in commonArtifactReplacements) {
            normalized = normalized.replace(bad, good)
        }
        normalized = maybeRepairMojibake(normalized)
        normalized = normalized.replace(Regex("[ÃÂã¼½¾]{2,}"), "")
        normalized = normalizeQuestionMarkArtifacts(normalized)
        return TextSanitizer.sanitizeText(normalized)
    }

    private fun normalizeQuestionMarkArtifacts(input: String): String {
        var text = input
        text = text.replace(Regex("^\\?+(?=[\\p{L}\\p{IsHan}])"), "")
        text = text.replace(Regex("(?<=[\\p{IsHan}])\\?(?=[\\p{IsHan}])"), "，")
        text = text.replace(Regex("，{2,}"), "，")
        text = text.replace(Regex("[，]([。！？])"), "$1")
        text = text.replace(Regex("(?<=[\\p{IsHan}])\\?(?=\\s|$)"), "。")
        return text
    }

    private fun maybeRepairMojibake(input: String): String {
        if (input.isBlank()) return input
        if (!mojibakeMarkers.containsMatchIn(input)) return input

        val candidates = buildList {
            add(input)
            runCatching { String(input.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) }.getOrNull()?.let(::add)
            runCatching { String(input.toByteArray(windows1252), Charsets.UTF_8) }.getOrNull()?.let(::add)
            runCatching { String(input.toByteArray(gb18030), Charsets.UTF_8) }.getOrNull()?.let(::add)
        }

        return candidates
            .distinct()
            .maxByOrNull(::score)
            ?: input
    }

    private fun sanitizeChunkPreservingWhitespace(input: String): String {
        if (input.isEmpty()) return input
        val leadingWhitespace = input.takeWhile { it.isWhitespace() }
        val trailingWhitespace = input.takeLastWhile { it.isWhitespace() }
        val coreStart = leadingWhitespace.length
        val coreEnd = input.length - trailingWhitespace.length
        if (coreStart >= coreEnd) return input
        val core = input.substring(coreStart, coreEnd)
        val sanitizedCore = TextSanitizer.sanitizeText(core)
        return leadingWhitespace + sanitizedCore + trailingWhitespace
    }

    private fun score(text: String): Int {
        var value = 0
        for (ch in text) {
            when {
                ch == '\uFFFD' -> value -= 10
                ch.code in 0x4E00..0x9FFF -> value += 3
                ch.code in 0x3400..0x4DBF -> value += 2
                ch in "，。！？；：（）《》“”‘’" -> value += 2
                ch.code in 0x20..0x7E -> value += 1
            }
        }
        if (mojibakeMarkers.containsMatchIn(text)) {
            value -= 12
        }
        return value
    }
}