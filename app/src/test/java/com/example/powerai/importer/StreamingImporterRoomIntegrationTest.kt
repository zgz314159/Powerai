package com.example.powerai.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.data.importer.StreamingJsonResourceImporter
import com.example.powerai.core.data.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import com.example.powerai.core.data.dao.KnowledgeDao

@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class StreamingImporterRoomIntegrationTest {
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun importSmallJson_writesToRoomAndRebuildsFts() {
        val json = """[
            {"entryId":"e1","unitName":"u1","jobTitle":"t1","contentMarkdown":"hello world","position":1},
            {"entryId":"e2","unitName":"u2","jobTitle":"t2","contentMarkdown":"another entry","position":2},
            {"entryId":"e3","unitName":"u3","jobTitle":"t3","contentMarkdown":"foo","blocks":{"src":"file:///android_asset/img.png"},"tags":["foo","bar"]}
        ]
        """.trimIndent()

        val input = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        val dao = db.knowledgeDao()

        runBlocking {
            val importer = StreamingJsonResourceImporter(dao)
            importer.importFromJson(input, batchSize = 1, trace = null, fallbackFileName = "test.json", fallbackFileId = "test1").collect { /* progress ignored */ }

            val all = dao.getAll()
            assertEquals(3, all.size)
            // dump for debug
            all.forEach { println("entity=$it") }
            // verify tags or image entry
            // find the entry that has tags or the special title
            val e3 = all.find { it.keywordsSerialized.contains("foo") || it.title == "t3" }
            assertNotNull(e3)
            e3?.let {
                println("e3 keywords='${it.keywordsSerialized}' imageUris=${it.imageUris}")
                assertTrue(it.keywordsSerialized.contains("foo"))
                assertNotNull(it.imageUris)
            }

            val fts = dao.countFts()
            assertEquals(3, fts)
        }
    }

    @Test
    fun importObjectRootJson_handlesEntries() {
        val json = """{ "entries": [
            {"entryId":"e1","unitName":"u1","jobTitle":"t1","contentMarkdown":"hello"}
        ] }""".trimIndent()

        val input = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        val dao = db.knowledgeDao()

        runBlocking {
            val importer = StreamingJsonResourceImporter(dao)
            importer.importFromJson(input, batchSize = 10, trace = null, fallbackFileName = "obj.json", fallbackFileId = "obj1").collect {}
            val all = dao.getAll()
            assertEquals(1, all.size)
            assertEquals("t1", all.first().title)
        }
    }
}
