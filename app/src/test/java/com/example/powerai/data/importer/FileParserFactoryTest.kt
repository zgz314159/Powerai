package com.example.powerai.data.importer

import android.content.ContentResolver
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

class FileParserFactoryTest {
    private val fakeResolver: ContentResolver = Mockito.mock(ContentResolver::class.java)

    @Test
    fun `create returns TxtParser for txt filenames`() {
        val parser = FileParserFactory.create("document.TXT", fakeResolver)
        assertTrue(parser is TxtParser)
    }

    @Test
    fun `create returns PdfParser for pdf filenames`() {
        val parser = FileParserFactory.create("report.pdf", fakeResolver)
        assertTrue(parser is PdfParser)
    }

    @Test
    fun `create returns DocxParser for docx and doc filenames`() {
        val p1 = FileParserFactory.create("letter.docx", fakeResolver)
        val p2 = FileParserFactory.create("letter.DOC", fakeResolver)
        assertTrue(p1 is DocxParser)
        assertTrue(p2 is DocxParser)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create throws for unsupported extensions`() {
        FileParserFactory.create("archive.zip", fakeResolver)
    }
}
