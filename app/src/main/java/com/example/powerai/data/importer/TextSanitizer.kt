package com.example.powerai.data.importer

import java.text.Normalizer

object TextSanitizer {
    // Remove invisible characters and normalize to NFC, simple mojibake fixes
    fun sanitizeText(input: String): String {
        var s = input
        // Normalize
        s = Normalizer.normalize(s, Normalizer.Form.NFC)
        // Remove control characters except newlines and tabs
        s = s.replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")
        // Replace common mojibake sequences (simple heuristics)
        s = s.replace("Ã©", "é")
        s = s.replace("Ã¨", "è")
        s = s.replace("Ã¤", "ä")
        s = s.replace("â€”", "—")
        // Trim
        return s.trim()
    }

    /**
     * Normalize text for local search indexing and matching.
     *
     * Rules:
     * - NFC normalize + sanitize control chars
     * - keep only letters/digits (including CJK), map others to spaces
     * - lowercase latin
     * - collapse whitespace
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
            } else {
                if (!lastWasSpace) {
                    out.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return out.toString().trim().replace(Regex("\\s+"), " ")
    }
}
