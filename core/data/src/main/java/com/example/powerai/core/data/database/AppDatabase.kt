package com.example.powerai.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.dao.VisionCacheDao
import com.example.powerai.core.data.dao.EmbeddingDao
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.entity.KnowledgeFtsEntity
import com.example.powerai.core.data.entity.VisionCacheEntity
import com.example.powerai.core.data.entity.EmbeddingMetadataEntity

@Database(
    entities = [
        KnowledgeEntity::class,
        KnowledgeFtsEntity::class,
        ImportedFileEntity::class,
        VisionCacheEntity::class,
        EmbeddingMetadataEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun visionCacheDao(): VisionCacheDao
    abstract fun embeddingDao(): EmbeddingDao
}
