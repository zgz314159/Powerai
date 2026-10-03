package com.example.powerai.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.dao.VisionCacheDao
import com.example.powerai.core.data.dao.EmbeddingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "powerai.db"
        )
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    // Explicit type keeps Dagger/KSP validation off the anonymous-object declaration.
    private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Version 5 added VisionCacheEntity and EmbeddingMetadataEntity
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `vision_cache` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `key` TEXT NOT NULL, `value` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `embedding_metadata` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileId` TEXT NOT NULL, `totalBlocks` INTEGER NOT NULL, `embeddedBlocks` INTEGER NOT NULL, `status` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL)"
            )
        }
    }

    // Additive only: package attribution for KB rows + a content fingerprint for imported files.
    // Existing rows keep packageId = NULL and contentSha256 = '' (legacy; never auto-replaced).
    @Suppress("MagicNumber")
    val MIGRATION_5_6: Migration =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `knowledge` ADD COLUMN `packageId` TEXT")
                db.execSQL("ALTER TABLE `imported_files` ADD COLUMN `contentSha256` TEXT NOT NULL DEFAULT ''")
            }
        }

    @Provides
    fun provideKnowledgeDao(db: AppDatabase): KnowledgeDao = db.knowledgeDao()

    @Provides
    fun provideVisionCacheDao(db: AppDatabase): VisionCacheDao = db.visionCacheDao()

    @Provides
    fun provideEmbeddingDao(db: AppDatabase): EmbeddingDao = db.embeddingDao()
}
