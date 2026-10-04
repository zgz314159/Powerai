package com.example.powerai.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.powerai.core.data.entity.VisionCacheEntity

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

    /** Drop the cache entries of specific knowledge rows, leaving other packages' entries intact. */
    @Query("DELETE FROM vision_cache WHERE entityId IN (:entityIds)")
    suspend fun deleteForEntities(entityIds: List<Long>): Int

    /**
     * Drop the whole vision cache. It is keyed by knowledge entity id (`entityId`), so a
     * user-confirmed rebuild that replaces the knowledge rows must invalidate it.
     */
    @Query("DELETE FROM vision_cache")
    suspend fun deleteAll(): Int
}
