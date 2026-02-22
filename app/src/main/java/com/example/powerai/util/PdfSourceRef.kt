package com.example.powerai.util

/**
 * Encodes a PDF import origin into the existing `KnowledgeEntity.source` string without schema changes.
 *
 * Format: `pdf:{sha256Hex}::{fileName}`
 */
object PdfSourceRef {
    private val pattern = Regex("^pdf:([0-9a-f]{64})::(.*)$", RegexOption.IGNORE_CASE)

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
}
