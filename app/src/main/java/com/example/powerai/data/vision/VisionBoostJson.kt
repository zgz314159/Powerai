package com.example.powerai.data.vision

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal object VisionBoostJson {

    fun extractChatContent(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        val candidate = runCatching {
            val root = JsonParser().parse(trimmed)
            extractFromRoot(root)
        }.getOrNull().orEmpty()

        return stripCodeFences(candidate).trim()
    }

    private fun extractFromRoot(root: JsonElement): String {
        if (!root.isJsonObject) return ""
        val obj = root.asJsonObject

        // Most common: OpenAI-style { choices: [ { message: { content } } ] }
        extractFromChoices(obj.getAsJsonArray("choices"))?.let { return it }

        // Some gateways wrap with { data: { choices: [...] } }
        obj.getAsJsonObject("data")?.let { dataObj ->
            extractFromChoices(dataObj.getAsJsonArray("choices"))?.let { return it }
        }

        // Fallback common fields
        obj.getAsStringOrNull("result")?.let { return it }
        obj.getAsStringOrNull("output_text")?.let { return it }
        obj.getAsStringOrNull("content")?.let { return it }
        obj.getAsStringOrNull("text")?.let { return it }

        return ""
    }

    private fun extractFromChoices(choices: JsonArray?): String? {
        if (choices == null || choices.size() == 0) return null

        val sb = StringBuilder()
        for (el in choices) {
            if (!el.isJsonObject) continue
            val choice = el.asJsonObject

            // message.content
            choice.getAsJsonObject("message")?.getAsStringOrNull("content")?.let { c ->
                if (c.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(c)
                }
            }

            // delta.content (stream-like)
            choice.getAsJsonObject("delta")?.getAsStringOrNull("content")?.let { c ->
                if (c.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(c)
                }
            }

            // Some providers use { choices: [ { text: "..." } ] }
            choice.getAsStringOrNull("text")?.let { c ->
                if (c.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(c)
                }
            }

            // Rare: { choices: [ { content: "..." } ] }
            choice.getAsStringOrNull("content")?.let { c ->
                if (c.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(c)
                }
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun stripCodeFences(s: String): String {
        val t = s.trim()
        if (!t.startsWith("```")) return s
        // Remove first/last fence blocks while keeping inner content.
        val lines = t.lines()
        if (lines.size < 2) return s
        val start = lines.indexOfFirst { it.trim().startsWith("```") }
        val end = lines.indexOfLast { it.trim().startsWith("```") }
        if (start == -1 || end == -1 || end <= start) return s
        return lines.subList(start + 1, end).joinToString("\n")
    }

    private fun JsonObject.getAsStringOrNull(key: String): String? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        val p = el.asJsonPrimitive
        return if (p.isString) p.asString else null
    }

    private fun JsonObject.getAsJsonObject(key: String): JsonObject? {
        val el = get(key) ?: return null
        return if (el.isJsonObject) el.asJsonObject else null
    }

    private fun JsonObject.getAsJsonArray(key: String): JsonArray? {
        val el = get(key) ?: return null
        return if (el.isJsonArray) el.asJsonArray else null
    }
}
