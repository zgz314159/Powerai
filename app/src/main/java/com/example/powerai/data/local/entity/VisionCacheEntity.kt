package com.example.powerai.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "vision_cache",
    primaryKeys = ["entityId", "blockId"]
)
data class VisionCacheEntity(
    val entityId: Long,
    val blockId: String,
    val imageUri: String?,
    val markdown: String,
    val updatedAtMs: Long
)
