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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v6 → v7 only adds `imported_files.sourceTreeUri` (KI-57); existing rows keep '' and are never
 * deleted.
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration6To7Test {
    private val dbName = "powerai-migration-6-7.db"
    private lateinit var context: Context
    private var room: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath(dbName).delete()
        seedV6Database()
    }

    @After
    fun tearDown() {
        room?.close()
        context.getDatabasePath(dbName).delete()
    }

    private fun seedV6Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(V6_KNOWLEDGE)
            db.execSQL(V6_KNOWLEDGE_FTS)
            V6_FTS_TRIGGERS.forEach { db.execSQL(it) }
            db.execSQL(V6_IMPORTED_FILES)
            db.execSQL(V6_VISION_CACHE)
            db.execSQL(V6_EMBEDDING_METADATA)
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, 'placeholder-v6')",
            )
            db.version = 6
            db.execSQL(
                "INSERT INTO `imported_files` " +
                    "(`fileId`,`fileName`,`timestamp`,`status`,`contentSha256`) " +
                    "VALUES ('user:abc','UserKb',99,'imported','deadbeef')",
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `v6 to v7 migration preserves imported rows and adds empty sourceTreeUri`() {
        room =
            Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(DatabaseModule.MIGRATION_6_7)
                .allowMainThreadQueries()
                .build()
        val imported = runBlocking { requireNotNull(room).knowledgeDao().getImportedFile("user:abc") }
        assertEquals("imported", imported?.status)
        assertEquals("deadbeef", imported?.contentSha256)
        assertEquals("", imported?.sourceTreeUri)
    }

    private companion object {
        const val V6_KNOWLEDGE =
            "CREATE TABLE IF NOT EXISTS `knowledge` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `content` TEXT NOT NULL, `source` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `keywordsSerialized` TEXT NOT NULL, " +
                "`contentNormalized` TEXT NOT NULL, `searchContent` TEXT NOT NULL, " +
                "`pageNumber` INTEGER, `sortOrder` INTEGER, `contentBlocksJson` TEXT, `bboxJson` TEXT, " +
                "`imageUris` TEXT, `vector_checksum` TEXT NOT NULL, `indexed_at` INTEGER NOT NULL, " +
                "`packageId` TEXT)"

        const val V6_KNOWLEDGE_FTS =
            "CREATE VIRTUAL TABLE IF NOT EXISTS `knowledge_fts` USING FTS4(`title` TEXT NOT NULL, " +
                "`source` TEXT NOT NULL, `searchContent` TEXT NOT NULL, content=`knowledge`)"

        val V6_FTS_TRIGGERS =
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

        const val V6_IMPORTED_FILES =
            "CREATE TABLE IF NOT EXISTS `imported_files` (`fileId` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                "`contentSha256` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`fileId`))"

        const val V6_VISION_CACHE =
            "CREATE TABLE IF NOT EXISTS `vision_cache` (`entityId` INTEGER NOT NULL, `blockId` TEXT NOT NULL, " +
                "`imageUri` TEXT, `markdown` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`entityId`, `blockId`))"

        const val V6_EMBEDDING_METADATA =
            "CREATE TABLE IF NOT EXISTS `embedding_metadata` (`id` INTEGER NOT NULL, `fileName` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    }
}
