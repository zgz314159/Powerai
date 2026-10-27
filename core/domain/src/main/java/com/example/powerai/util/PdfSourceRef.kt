package com.example.powerai.util

/**
 * Encodes a PDF import origin into the existing `KnowledgeEntity.source` string without schema changes.
 *
 * Format: `pdf:{sha256Hex}::{fileName}`
 */
object PdfSourceRef {
    private val pattern = Regex("^pdf:([0-9a-f]{64})::(.*)$", RegexOption.IGNORE_CASE)
    private val opaqueHexPattern = Regex("^(?:sha256:)?[0-9a-f]{24,64}$", RegexOption.IGNORE_CASE)

    private const val ASSETS_KB_PREFIX = "assets/kb/"
    private const val ASSETS_LEGACY_PREFIX = "assets/原件截图/"

    data class Ref(val fileId: String, val fileName: String)

    fun encode(fileId: String, fileName: String): String {
        val safeName = fileName.replace("\n", " ").replace("\r", " ")
        return "pdf:$fileId::$safeName"
    }

    fun parse(source: String?): Ref? {
        val s = source?.trim().orEmpty()
        if (s.isBlank()) return null
        val m = pattern.find(s) ?: return null
        val fileId = m.groupValues[1].lowercase()
        val fileName = m.groupValues[2]
        if (fileId.length != 64 || fileName.isBlank()) return null
        return Ref(fileId = fileId, fileName = fileName)
    }

    fun display(source: String?): String {
        val ref = parse(source)
        if (ref != null) return ref.fileName

        val raw = source?.trim().orEmpty()
        if (raw.isBlank()) return ""

        val normalized = raw.replace('\\', '/')
        return when {
            normalized.startsWith(ASSETS_KB_PREFIX) -> normalized.removePrefix(ASSETS_KB_PREFIX)
            normalized.startsWith(ASSETS_LEGACY_PREFIX) -> normalized.removePrefix(ASSETS_LEGACY_PREFIX)
            else -> raw
        }
    }

    fun userVisibleSource(source: String?): String {
        return sanitizeDisplayPart(source).orEmpty()
    }

    fun cleanCompositeLabel(label: String?): String {
        val raw = label?.trim().orEmpty()
        if (raw.isBlank()) return ""
        return raw
            .split(Regex("\\s*[·•|｜]\\s*"))
            .mapNotNull(::sanitizeDisplayPart)
            .distinct()
            .joinToString(" · ")
    }

    fun buildUserVisibleLabel(vararg parts: String?): String {
        return parts
            .flatMap { part ->
                val trimmed = part?.trim().orEmpty()
                if (trimmed.isBlank()) emptyList() else trimmed.split(Regex("\\s*[·•|｜]\\s*"))
            }
            .mapNotNull(::sanitizeDisplayPart)
            .distinct()
            .joinToString(" · ")
    }

    private fun sanitizeDisplayPart(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null

        val displayed = display(raw).trim()
        if (displayed.isBlank()) return null
        if (displayed.startsWith("pdf:", ignoreCase = true)) return null

        val leafName = displayed
            .replace('\\', '/')
            .substringAfterLast('/')
            .trim()

        return when {
            looksOpaqueId(displayed) -> null
            leafName.isNotBlank() && looksOpaqueId(leafName) -> null
            else -> displayed
        }
    }

    private fun looksOpaqueId(value: String): Boolean {
        val normalized = value.trim().removePrefix("sha256:")
        return normalized.length >= 24 && opaqueHexPattern.matches(value.trim())
    }
}
