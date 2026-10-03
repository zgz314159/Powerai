package com.example.powerai.core.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class ImportIntoMemoryDaoBlockingTest {
    private val json =
        """
        [
            {"entryId":"e1","unitName":"u1","jobTitle":"t1","contentMarkdown":"hello","tags":["a","b"]},
            {"entryId":"e2","unitName":"u2","jobTitle":"t2","contentMarkdown":"world"}
        ]
        """.trimIndent()

    @Test
    fun streamingHelper_importsArrayAndReportsFts() {
        val input = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))

        val (imported, fts) =
            StreamingJsonResourceImporter.importIntoMemoryDaoBlocking(
                input,
                batchSize = 1,
                trace = null,
                fallbackFileName = "f",
                fallbackFileId = "id",
            )
        assertEquals(2, imported.toInt())
        assertEquals(2, fts)
    }

    @Test
    fun memoryDaoHelper_importsArrayAndReportsFts() {
        val input = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))

        val (imported, fts) =
            MemoryKnowledgeDao.importIntoMemoryDaoBlocking(
                input,
                batchSize = 1,
                trace = null,
                fallbackFileName = "f",
                fallbackFileId = "id",
            )
        assertEquals(2, imported.toInt())
        assertEquals(2, fts)
    }
}
