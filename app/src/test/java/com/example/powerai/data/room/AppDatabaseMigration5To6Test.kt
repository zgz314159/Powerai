package com.example.powerai.data.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An installed user's existing v5 database must upgrade to v6 **without data loss**: the migration
 * only adds `knowledge.packageId` (null for legacy rows) and
 * `imported_files.contentSha256` ('' for legacy rows) and deletes nothing.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration5To6Test {
    private val dbName = "powerai-migration-5-6.db"
    private lateinit var context: Context
    private var room: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath(dbName).delete()
        seedLegacyV5Database()
    }

    @After
    fun tearDown() {
        room?.close()
        context.getDatabasePath(dbName).delete()
    }

    /** Builds a v5 database with the exact v5 DDL Room generated for this app. */
    private fun seedLegacyV5Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(V5_KNOWLEDGE)
            db.execSQL(V5_KNOWLEDGE_FTS)
            V5_FTS_TRIGGERS.forEach { db.execSQL(it) }
            db.execSQL(V5_IMPORTED_FILES)
            db.execSQL(V5_VISION_CACHE)
            db.execSQL(V5_EMBEDDING_METADATA)
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, 'eaae5f997579919ec9f39e558e1e2238')",
            )
            db.version = 5
            db.execSQL(
                "INSERT INTO `knowledge` " +
                    "(`title`,`content`,`source`,`category`,`keywordsSerialized`,`contentNormalized`," +
                    "`searchContent`,`vector_checksum`,`indexed_at`) VALUES " +
                    "('legacy title','legacy content','pdf:legacy::a.pdf','unit',''," +
                    "'legacy content','legacy content','',0)",
            )
            db.execSQL(
                "INSERT INTO `imported_files` (`fileId`,`fileName`,`timestamp`,`status`) " +
                    "VALUES ('legacy-file','Legacy',123,'imported')",
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `v5 to v6 migration preserves legacy data and adds columns`() {
        room =
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(DatabaseModule.MIGRATION_5_6)
                .allowMainThreadQueries()
                .build()
        val dao = requireNotNull(room).knowledgeDao()

        val rows = runBlocking { dao.getAll() }
        assertEquals("legacy knowledge row preserved", 1, rows.size)
        assertEquals("legacy title", "legacy title", rows.first().title)
        assertNull("legacy row has no attributed package", rows.first().packageId)

        val imported = runBlocking { dao.getImportedFile("legacy-file") }
        assertEquals("legacy imported row preserved", "imported", imported?.status)
        assertEquals("legacy fingerprint is empty", "", imported?.contentSha256)

        // The new package-scoped delete must not touch unattributed legacy rows.
        val deleted = runBlocking { dao.deleteByPackageId("no-such-package") }
        assertEquals(0, deleted)
        assertEquals("no legacy row deleted", 1, runBlocking { dao.getAll() }.size)
    }

    private companion object {
        const val V5_KNOWLEDGE =
            "CREATE TABLE IF NOT EXISTS `knowledge` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `content` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `keywordsSerialized` TEXT NOT NULL, " +
                "`contentNormalized` TEXT NOT NULL, `searchContent` TEXT NOT NULL, " +
                "`pageNumber` INTEGER, `sortOrder` INTEGER, `contentBlocksJson` TEXT, `bboxJson` TEXT, " +
                "`imageUris` TEXT, `vector_checksum` TEXT NOT NULL, `indexed_at` INTEGER NOT NULL)"

        const val V5_KNOWLEDGE_FTS =
            "CREATE VIRTUAL TABLE IF NOT EXISTS `knowledge_fts` USING FTS4(`title` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, `searchContent` TEXT NOT NULL, content=`knowledge`)"

        val V5_FTS_TRIGGERS =
            listOf(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_knowledge_fts_BEFORE_UPDATE " +
                    "BEFORE UPDATE ON `knowledge` BEGIN DELETE FROM `knowledge_fts` WHERE `docid`=OLD.`rowid`; END",
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_knowledge_fts_BEFORE_DELETE " +
                    "BEFORE DELETE ON `knowledge` BEGIN DELETE FROM `knowledge_fts` WHERE `docid`=OLD.`rowid`; END",
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_knowledge_fts_AFTER_UPDATE " +
                    "AFTER UPDATE ON `knowledge` BEGIN INSERT INTO `knowledge_fts`(`docid`, `title`, `source`, `searchContent`) " +
                    "VALUES (NEW.`rowid`, NEW.`title`, NEW.`source`, NEW.`searchContent`); END",
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_knowledge_fts_AFTER_INSERT " +
                    "AFTER INSERT ON `knowledge` BEGIN INSERT INTO `knowledge_fts`(`docid`, `title`, `source`, `searchContent`) " +
                    "VALUES (NEW.`rowid`, NEW.`title`, NEW.`source`, NEW.`searchContent`); END",
            )

        const val V5_IMPORTED_FILES =
            "CREATE TABLE IF NOT EXISTS `imported_files` (`fileId` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`fileId`))"

        const val V5_VISION_CACHE =
            "CREATE TABLE IF NOT EXISTS `vision_cache` (`entityId` INTEGER NOT NULL, `blockId` TEXT NOT NULL, " +
                "`imageUri` TEXT, `markdown` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`entityId`, `blockId`))"

        const val V5_EMBEDDING_METADATA =
            "CREATE TABLE IF NOT EXISTS `embedding_metadata` (`id` INTEGER NOT NULL, `fileName` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    }
}
