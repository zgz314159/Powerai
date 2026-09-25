package com.example.powerai.data.importer

import android.content.ContentResolver
import android.net.Uri
import com.example.powerai.core.data.entity.KnowledgeEntity

/**
 * Generic interface for parsing a URI into batches of [[KnowledgeEntity]] objects.
 * Implementations may compute a stable file ID (usually SHA-256) and optionally
 * report progress using more specialized callbacks; the simplified signature
 * used by callers omits these extra hooks for clarity.
 */
interface FileParser {
    /**
     * Parse the given URI, calling `onBatchReady` whenever a batch of entities is
     * available. Returns a stable file identifier (usually SHA-256 hex) which is
     * used for deduplication by callers.
     */
    suspend fun parse(
        uri: Uri,
        fileName: String,
        batchSize: Int = ImportDefaults.DEFAULT_BATCH_SIZE,
        onBatchReady: suspend (List<KnowledgeEntity>) -> Unit
    ): String
}

/**
 * Factory abstraction for choosing a parser. An interface helps with test
 * injection; the concrete object below implements it.
 */
interface FileParserFactoryType {
    fun create(fileName: String, contentResolver: ContentResolver): FileParser
}

/**
 * Simple factory to choose an appropriate parser based on file name/extension.
 * Centralizing the logic reduces duplication across various import entrypoints.
 */
internal object FileParserFactory : FileParserFactoryType {
    override fun create(fileName: String, contentResolver: ContentResolver): FileParser {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".txt") -> TxtParser(contentResolver)
            lower.endsWith(".pdf") -> PdfParser(contentResolver)
            lower.endsWith(".docx") || lower.endsWith(".doc") -> DocxParser(contentResolver)
            else -> throw IllegalArgumentException("Unsupported file type: $fileName")
        }
    }
}
