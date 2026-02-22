package com.example.powerai.data.importer

import android.content.ContentResolver
import android.net.Uri
import com.example.powerai.data.local.entity.KnowledgeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.security.MessageDigest

/**
 * Lightweight DOCX/DOC fallback parser: does not require Apache POI at
 * compile-time. It reads file bytes and attempts a best-effort UTF-8
 * extraction. Replace with Apache POI implementation when dependency is
 * intentionally added.
 */
class DocxParser(private val contentResolver: ContentResolver) {

    suspend fun parse(
        uri: Uri,
        fileName: String,
        batchSize: Int = com.example.powerai.data.importer.ImportDefaults.DEFAULT_BATCH_SIZE,
        onBatchReady: suspend (List<KnowledgeEntity>) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open uri")
        val buffered = BufferedInputStream(input)
        val all = buffered.readBytes()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(all)
        buffered.close()

        val text = try { String(all, Charsets.UTF_8) } catch (_: Exception) { String(all, Charsets.ISO_8859_1) }
        val parts = text.split(Regex("\\r?\\n\\s*\\r?\\n+"))
        val batch = ArrayList<KnowledgeEntity>(batchSize)
        parts.forEach { p ->
            val clean = TextSanitizer.sanitizeText(p)
            if (clean.isNotBlank()) batch.add(KnowledgeEntity(title = "", content = clean, source = fileName))
            if (batch.size >= batchSize) {
                onBatchReady(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) onBatchReady(batch.toList())
        val fileId = digest.digest().joinToString("") { "%02x".format(it) }
        fileId
    }
}
