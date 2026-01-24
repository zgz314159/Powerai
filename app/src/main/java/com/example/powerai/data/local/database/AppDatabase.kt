package com.example.powerai.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.example.powerai.data.local.entity.KnowledgeFtsEntity
import com.example.powerai.data.local.entity.ImportedFileEntity

@Database(entities = [KnowledgeEntity::class, KnowledgeFtsEntity::class, ImportedFileEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knowledgeDao(): KnowledgeDao
}
