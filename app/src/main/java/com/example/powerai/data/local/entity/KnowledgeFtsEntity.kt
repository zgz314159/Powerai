package com.example.powerai.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "knowledge_fts")
@Fts4(contentEntity = KnowledgeEntity::class)
data class KnowledgeFtsEntity(
    val content: String
)
