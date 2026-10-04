package com.example.powerai.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.security.MessageDigest

/**
 * The shared PDF association step must only store a PDF whose SHA-256 matches the file id the KB
 * declares; a mismatching PDF is reported but never associated.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class PdfAssociationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val uri: Uri = Uri.parse("content://powerai.test/pdf")

    @Test
    fun `matching sha is associated and stored`() {
        val bytes = "pdf-bytes".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        register(bytes)

        val result = PdfAssociation.associate(context, uri, sha)

        assertEquals(PdfAssociationResult.Associated, result)
        assertTrue("pdf saved under its sha", PdfStorage.hasPdf(context, sha))
    }

    @Test
    fun `mismatching sha is rejected and not stored`() {
        val bytes = "wrong-pdf".toByteArray()
        val expected = "b".repeat(64)
        register(bytes)

        val result = PdfAssociation.associate(context, uri, expected)

        assertTrue("expected mismatch, got $result", result is PdfAssociationResult.Mismatch)
        assertEquals(expected, (result as PdfAssociationResult.Mismatch).expected)
        assertFalse("mismatching pdf must not be associated", PdfStorage.hasPdf(context, expected))
    }

    private fun register(bytes: ByteArray) {
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
    }
}
