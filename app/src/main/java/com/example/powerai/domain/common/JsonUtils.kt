package com.example.powerai.domain.common

/**
 * Lightweight JSON helpers used across the domain layer.
 */
object JsonUtils {
    fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    fun unescapeJson(s: String): String {
        return s.replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}
