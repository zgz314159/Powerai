package com.example.powerai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.powerai.data.local.entity.EmbeddingMetadataEntity

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: EmbeddingMetadataEntity)

    @Query("SELECT fileName FROM embedding_metadata WHERE id = :id LIMIT 1")
    suspend fun getFileName(id: Long): String?
}
