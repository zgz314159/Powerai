package com.example.powerai.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.powerai.core.data.entity.EmbeddingMetadataEntity

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: EmbeddingMetadataEntity)

    @Query("SELECT fileName FROM embedding_metadata WHERE id = :id LIMIT 1")
    suspend fun getFileName(id: Long): String?

    /**
     * Drop the whole embedding-metadata table. Its primary key is the knowledge entity id, so a
     * user-confirmed rebuild that replaces the knowledge rows must invalidate it.
     */
    @Query("DELETE FROM embedding_metadata")
    suspend fun deleteAll(): Int
}
