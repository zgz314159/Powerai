package com.example.powerai.core.model.util

/**
 * Lightweight JSON helpers used across the domain and data layers.
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
