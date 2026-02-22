package com.example.powerai.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.data.importer.StreamingJsonResourceImporter
import com.example.powerai.data.local.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

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
            {"entryId":"e2","unitName":"u2","jobTitle":"t2","contentMarkdown":"another entry","position":2}
        ]
        """.trimIndent()

        val input = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        val dao = db.knowledgeDao()

        runBlocking {
            val importer = StreamingJsonResourceImporter(dao)
            importer.importFromJson(input, batchSize = 1, trace = null, fallbackFileName = "test.json", fallbackFileId = "test1").collect { /* progress ignored */ }

            val all = dao.getAll()
            assertEquals(2, all.size)

            val fts = dao.countFts()
            assertEquals(2, fts)
        }
    }
}
