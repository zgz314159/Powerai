package com.example.powerai.util

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

/** Result of verifying a user-picked PDF against the expected SHA-256 before associating it. */
sealed interface PdfAssociationResult {
    data object Associated : PdfAssociationResult

    data class Mismatch(
        val expected: String,
        val actual: String,
    ) : PdfAssociationResult

    data class Failed(
        val reason: String,
    ) : PdfAssociationResult
}

/**
 * Single source of truth for "associate an original PDF with a KB entry": reads the picked
 * document, verifies its SHA-256 against the file id the KB declares (`pdf:{sha}::{name}`), and
 * only then stores it under `filesDir/imported_pdfs/{sha}.pdf`. A mismatching PDF is never
 * associated, so a wrong file can never be silently linked to the knowledge base.
 */
object PdfAssociation {
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    fun associate(
        context: Context,
        uri: Uri,
        expectedSha256: String,
    ): PdfAssociationResult {
        val bytes =
            try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return PdfAssociationResult.Failed("无法读取所选文件")
            } catch (t: Throwable) {
                return PdfAssociationResult.Failed(t.message ?: "无法读取所选文件")
            }

        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            return PdfAssociationResult.Mismatch(expected = expectedSha256, actual = actual)
        }

        return try {
            PdfStorage.savePdfBytes(context, bytes, expectedSha256)
            PdfAssociationResult.Associated
        } catch (t: Throwable) {
            PdfAssociationResult.Failed(t.message ?: "保存 PDF 失败")
        }
    }
}
