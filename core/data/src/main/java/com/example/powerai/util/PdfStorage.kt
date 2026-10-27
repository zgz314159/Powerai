package com.example.powerai.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File

object PdfStorage {
    private const val DIR = "imported_pdfs"

    fun getPdfFile(context: Context, fileId: String): File {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$fileId.pdf")
    }

    fun hasPdf(context: Context, fileId: String): Boolean {
        return try {
            getPdfFile(context, fileId).exists()
        } catch (_: Throwable) {
            false
        }
    }

    fun savePdfFromUri(context: Context, resolver: ContentResolver, uri: Uri, fileId: String) {
        val dst = getPdfFile(context, fileId)
        resolver.openInputStream(uri)?.use { input ->
            dst.outputStream().use { out ->
                input.copyTo(out)
            }
        } ?: throw IllegalArgumentException("Cannot open uri: $uri")
    }

    fun savePdfBytes(context: Context, bytes: ByteArray, fileId: String) {
        val dst = getPdfFile(context, fileId)
        dst.outputStream().use { out ->
            out.write(bytes)
        }
    }
}
