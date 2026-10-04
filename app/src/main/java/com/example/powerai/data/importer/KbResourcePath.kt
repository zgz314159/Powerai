package com.example.powerai.data.importer

/**
 * Validates the contract-allowed **relative** resource paths inside a KB output directory
 * (e.g. `shots/p29_tbl1.png`). Absolute paths, backslashes and `..` traversal are rejected so a
 * hand-crafted KB can never escape the picked directory or the app-private mirror.
 */
internal object KbResourcePath {
    private val DRIVE_PREFIX = Regex("^[a-zA-Z]:")

    sealed interface Result {
        data class Valid(val path: String) : Result

        data class Invalid(val reason: String) : Result
    }

    fun validate(raw: String): Result {
        val trimmed = raw.trim()
        val reason = invalidReason(trimmed)
        if (reason != null) return Result.Invalid(reason)
        val normalized = trimmed.split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")
        return if (normalized.isEmpty()) Result.Invalid("empty path") else Result.Valid(normalized)
    }

    private fun invalidReason(value: String): String? =
        when {
            value.isEmpty() -> "empty path"
            value.contains('\\') -> "backslash not allowed"
            value.startsWith("/") || value.startsWith("~") -> "absolute path not allowed"
            value.contains("://") -> "absolute uri not allowed"
            DRIVE_PREFIX.containsMatchIn(value) -> "drive path not allowed"
            value.split('/').any { it == ".." } -> "path traversal not allowed"
            else -> null
        }
}
