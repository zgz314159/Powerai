package com.example.powerai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.powerai.data.local.entity.VisionCacheEntity

@Dao
interface VisionCacheDao {
    @Query("SELECT * FROM vision_cache WHERE entityId = :entityId")
    suspend fun getAllForEntity(entityId: Long): List<VisionCacheEntity>

    @Query("SELECT * FROM vision_cache WHERE entityId = :entityId AND blockId = :blockId LIMIT 1")
    suspend fun getOne(entityId: Long, blockId: String): VisionCacheEntity?

    @Query("DELETE FROM vision_cache WHERE entityId = :entityId AND blockId = :blockId")
    suspend fun deleteOne(entityId: Long, blockId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VisionCacheEntity)

    @Query("DELETE FROM vision_cache WHERE entityId = :entityId")
    suspend fun deleteForEntity(entityId: Long)
}
