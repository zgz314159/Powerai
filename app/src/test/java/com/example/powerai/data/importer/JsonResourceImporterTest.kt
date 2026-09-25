package com.example.powerai.data.importer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class JsonResourceImporterTest {
    @Test
    fun importSimpleArray_writesEntries() {
        val json = """[
            {"entryId":"e1","unitName":"u1","jobTitle":"t1","contentMarkdown":"hello","tags":["a","b"]},
            {"entryId":"e2","unitName":"u2","jobTitle":"t2","contentMarkdown":"world"}
        ]""".trimIndent()
        val input = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))

        val (imported, fts) = StreamingJsonResourceImporter.importIntoMemoryDaoBlocking(input, batchSize = 1, trace = null, fallbackFileName = "f", fallbackFileId = "id")
        assertEquals(2, imported.toInt())
        assertEquals(2, fts)
    }
}
