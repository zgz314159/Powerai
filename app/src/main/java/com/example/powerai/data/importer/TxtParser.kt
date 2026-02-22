package com.example.powerai.data.importer

import android.content.ContentResolver
import android.net.Uri
import com.example.powerai.data.local.entity.KnowledgeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.universalchardet.UniversalDetector
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.security.MessageDigest

class TxtParser(private val contentResolver: ContentResolver) {

    suspend fun parse(
        uri: Uri,
        fileName: String,
        batchSize: Int = com.example.powerai.data.importer.ImportDefaults.DEFAULT_BATCH_SIZE,
        onBatchReady: suspend (List<KnowledgeEntity>) -> Unit,
        onProgressBytes: (readBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open uri")
        val buffered = BufferedInputStream(input)

        // compute hash while reading
        val digest = MessageDigest.getInstance("SHA-256")

        // detect charset using juniversalchardet
        val detector = UniversalDetector(null)
        val probe = ByteArray(4096)
        var read = buffered.read(probe)
        var totalRead = 0L
        while (read > 0 && !detector.isDone) {
            detector.handleData(probe, 0, read)
            digest.update(probe, 0, read)
            totalRead += read
            read = buffered.read(probe)
        }
        detector.dataEnd()
        val charset = detector.detectedCharset ?: "UTF-8"
        // reopen stream because we consumed initial bytes
        buffered.close()
        val input2 = contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot reopen uri")
        val reader = BufferedReader(InputStreamReader(input2, Charset.forName(charset)))

        val batch = ArrayList<KnowledgeEntity>(batchSize)
        var line: String?
        var imported = 0
        var bytesSoFar = 0L
        val totalBytes: Long? = try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.length
        } catch (e: Exception) { null }

        while (true) {
            line = reader.readLine() ?: break
            bytesSoFar += line.toByteArray(Charset.forName(charset)).size
            onProgressBytes(bytesSoFar, totalBytes)
            val clean = TextSanitizer.sanitizeText(line)
            if (clean.isNotBlank()) {
                batch.add(KnowledgeEntity(title = "", content = clean, source = fileName))
            }
            if (batch.size >= batchSize) {
                onBatchReady(batch.toList())
                imported += batch.size
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) {
            onBatchReady(batch.toList())
            imported += batch.size
            batch.clear()
        }
        reader.close()
        // return final digest hex
        val fileId = digest.digest().joinToString("") { "%02x".format(it) }
        fileId
    }
}
