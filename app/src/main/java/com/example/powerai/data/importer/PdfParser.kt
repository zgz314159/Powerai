package com.example.powerai.data.importer

import android.content.ContentResolver
import android.net.Uri
import com.example.powerai.data.local.entity.KnowledgeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.security.MessageDigest

/**
 * Lightweight PDF parser fallback: does not require PDFBox at compile-time.
 * It reads the file as bytes and performs a naive text extraction by
 * interpreting bytes as UTF-8 (best-effort). Replace with PDFBox-based
 * implementation when adding the dependency back.
 */
class PdfParser(private val contentResolver: ContentResolver) {

    suspend fun parse(
        uri: Uri,
        fileName: String,
        batchSize: Int = 100,
        onBatchReady: suspend (List<KnowledgeEntity>) -> Unit,
        onProgressPages: (page: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open uri")
        val buffered = BufferedInputStream(input)
        val digest = MessageDigest.getInstance("SHA-256")
        val all = buffered.readBytes()
        digest.update(all)
        buffered.close()

        // Naive split: treat as UTF-8 text and split on double newlines
        val text = try {
            String(all, Charsets.UTF_8)
        } catch (_: Exception) {
            String(all, Charsets.ISO_8859_1)
        }
        val parts = text.split(Regex("\\r?\\n\\s*\\r?\\n+"))
        val batch = ArrayList<KnowledgeEntity>(batchSize)
        parts.forEachIndexed { idx, p ->
            val clean = TextSanitizer.sanitizeText(p)
            if (clean.isNotBlank()) batch.add(KnowledgeEntity(title = "", content = clean, source = fileName))
            if (batch.size >= batchSize) {
                onBatchReady(batch.toList())
                batch.clear()
            }
            onProgressPages(idx + 1, parts.size)
        }
        if (batch.isNotEmpty()) onBatchReady(batch.toList())
        val fileId = digest.digest().joinToString("") { "%02x".format(it) }
        fileId
    }
}
