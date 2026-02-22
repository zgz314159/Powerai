package com.example.powerai.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.dao.VisionCacheDao
import com.example.powerai.data.local.entity.ImportedFileEntity
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.example.powerai.data.local.entity.KnowledgeFtsEntity
import com.example.powerai.data.local.entity.VisionCacheEntity

@Database(
    entities = [
        KnowledgeEntity::class,
        KnowledgeFtsEntity::class,
        ImportedFileEntity::class,
        VisionCacheEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao

    abstract fun visionCacheDao(): VisionCacheDao
}
