package com.example.powerai.core.data.importer

import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * The production importer must read a v2 object root
 * (`{"fileMetadata":…,"entries":[…]}`) entry-by-entry and flush batches *before*
 * the tail of the stream is consumed. These tests pin that behaviour with an
 * input whose tail becomes unreadable, and pin that cancellation is never
 * swallowed or reported as success.
 */
class StreamingJsonObjectRootStreamingTest {
    /** Serves [prefix] bytes, then fails every further read to simulate an unreadable tail. */
    private class TruncatedTailInputStream(private val prefix: ByteArray) : InputStream() {
        private var index = 0

        override fun read(): Int {
            if (index < prefix.size) return prefix[index++].toInt() and 0xFF
            throw IOException("tail unavailable")
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            if (len == 0) return 0
            if (index >= prefix.size) throw IOException("tail unavailable")
            val n = minOf(len, prefix.size - index)
            System.arraycopy(prefix, index, b, off, n)
            index += n
            return n
        }
    }

    /** First DAO write fails with cancellation, proving it must propagate. */
    private class CancellingOnWriteDao(
        private val delegate: MemoryKnowledgeDao = MemoryKnowledgeDao(),
    ) : KnowledgeDao by delegate {
        override suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) {
            throw CancellationException("cancelled during batch write")
        }
    }

    private val sha = "a".repeat(64)
    private val declaredSource = "pdf:$sha::doc.pdf"

    private fun collect(
        dao: KnowledgeDao,
        stream: InputStream,
        batchSize: Int,
    ): List<ImportProgress> {
        val progress = mutableListOf<ImportProgress>()
        // The importer now propagates failures; the tests here observe the writes it streamed
        // before the tail failed (the manager adds the rollback boundary).
        runCatching {
            runBlocking {
                StreamingJsonResourceImporter(dao)
                    .importFromJson(
                        inputStream = stream,
                        batchSize = batchSize,
                        trace = null,
                        fallbackFileName = "doc.pdf",
                        fallbackFileId = "asset_hash",
                    )
                    .collect { progress.add(it) }
            }
        }
        return progress
    }

    @Test
    fun `first batch is written before the unreadable tail is reached`() {
        val prefix =
            """{"fileMetadata":{"schemaVersion":"2.0","fileId":"doc","fileName":"doc.pdf","source":"$declaredSource"},"entries":[""" +
                """{"entryId":"e1","jobTitle":"t1","contentMarkdown":"alpha"},""" +
                """{"entryId":"e2","jobTitle":"t2","contentMarkdown":"bravo"}"""
        val dao = MemoryKnowledgeDao()

        collect(dao, TruncatedTailInputStream(prefix.toByteArray(Charsets.UTF_8)), batchSize = 1)

        // The first entries must be flushed before the tail read fails; whole-root parsing cannot do
        // this. (Progress emitted just before the failure is not asserted here: the upstream failure
        // may discard still-buffered emissions.)
        val written = runBlocking { dao.getAll() }
        assertEquals(2, written.size)
    }

    @Test
    fun `a successful import advances progress and ends imported`() {
        val json =
            """{"fileMetadata":{"source":"$declaredSource"},"entries":[""" +
                """{"entryId":"e1","jobTitle":"t1","position":1,"contentMarkdown":"a"},""" +
                """{"entryId":"e2","jobTitle":"t2","position":2,"contentMarkdown":"b"}]}"""
        val dao = MemoryKnowledgeDao()

        val progress = collect(dao, ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)), batchSize = 1)

        assertEquals(
            "in_progress items are emitted per flushed batch",
            listOf(1L, 2L),
            progress.filter { it.status == "in_progress" }.map { it.importedItems },
        )
        assertEquals("imported", progress.last().status)
    }

    @Test
    fun `entries before fileMetadata keeps the declared pdf source`() {
        val json =
            """{"entries":[{"entryId":"e1","jobTitle":"t1","contentMarkdown":"alpha"}],""" +
                """"fileMetadata":{"schemaVersion":"2.0","source":"$declaredSource"}}"""
        val dao = MemoryKnowledgeDao()

        collect(dao, ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)), batchSize = 16)

        assertEquals(declaredSource, runBlocking { dao.getAll() }.single().source)
    }

    @Test
    fun `unknown root fields are skipped without affecting entries`() {
        val json =
            """{"fileMetadata":{"source":"$declaredSource"},""" +
                """"unknownRoot":{"nested":[1,2,3]},""" +
                """"entries":[{"entryId":"e1","jobTitle":"t1","contentMarkdown":"alpha"}],""" +
                """"trailingUnknown":"ignored"}"""
        val dao = MemoryKnowledgeDao()

        collect(dao, ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)), batchSize = 16)

        val rows = runBlocking { dao.getAll() }
        assertEquals(1, rows.size)
        assertEquals(declaredSource, rows.single().source)
    }

    @Test
    fun `cancellation during a batch write propagates instead of reporting success`() {
        val json =
            """{"fileMetadata":{"source":"$declaredSource"},""" +
                """"entries":[{"entryId":"e1","jobTitle":"t1","contentMarkdown":"alpha"}]}"""
        val progress = mutableListOf<ImportProgress>()
        var caught: Throwable? = null

        try {
            runBlocking {
                StreamingJsonResourceImporter(CancellingOnWriteDao())
                    .importFromJson(
                        inputStream = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)),
                        batchSize = 1,
                        trace = null,
                        fallbackFileName = "doc.pdf",
                        fallbackFileId = "asset_hash",
                    )
                    .collect { progress.add(it) }
            }
        } catch (c: CancellationException) {
            caught = c
        }

        assertTrue("CancellationException must propagate", caught is CancellationException)
        assertTrue("cancellation must not be reported as success/failure", progress.isEmpty())
    }
}
