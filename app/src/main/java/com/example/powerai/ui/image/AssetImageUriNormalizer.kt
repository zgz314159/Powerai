package com.example.powerai.ui.image

internal object AssetImageUriNormalizer {
    fun normalize(raw: String): String {
        var s = raw.trim()
        if (s.isBlank()) return ""

        if (s.startsWith("file:///android_asset/")) return s

        // Common legacy paths.
        if (s.contains("assets/images/")) {
            s = s.replace("assets/images/", "file:///android_asset/images/")
        }
        if (s.startsWith("/android_asset/")) {
            s = s.removePrefix("/")
            s = "file:///$s"
        }
        if (s.startsWith("images/")) {
            s = "file:///android_asset/$s"
        }

        // If it looks like a plain filename, assume images/.
        if (!s.contains("://") && !s.startsWith("/")) {
            s = "file:///android_asset/images/$s"
        }

        return s
    }
}
