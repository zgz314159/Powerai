package com.example.powerai.core.model.util

import java.text.Normalizer

object TextSanitizer {
    /**
     * Remove invisible characters and normalize to NFC, with a few simple mojibake fixes.
     */
    fun sanitizeText(input: String): String {
        var s = Normalizer.normalize(input, Normalizer.Form.NFC)
        s = s.replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")
        s = s.replace("Ã©", "é")
        s = s.replace("Ã¨", "è")
        s = s.replace("Ã¤", "ä")
        s = s.replace("â€™", "'")
        s = s.replace("â€œ", "\"")
        s = s.replace("â€", "\"")
        return s.trim()
    }

    /**
     * Normalize text for local search indexing and matching.
     */
    fun normalizeForSearch(input: String): String {
        val sanitized = sanitizeText(input)
        if (sanitized.isBlank()) return ""
        val out = StringBuilder(sanitized.length)
        var lastWasSpace = false
        for (ch in sanitized) {
            if (Character.isLetterOrDigit(ch)) {
                out.append(ch.lowercaseChar())
                lastWasSpace = false
            } else if (!lastWasSpace) {
                out.append(' ')
                lastWasSpace = true
            }
        }
        return out.toString().trim().replace(Regex("\\s+"), " ")
    }
}