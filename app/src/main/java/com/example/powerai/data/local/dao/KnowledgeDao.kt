package com.example.powerai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.powerai.data.local.entity.ImportedFileEntity
import com.example.powerai.data.local.entity.KnowledgeEntity
/**
 * 非 Room 的本地 DAO 接口（用于保持实现简单、避免注解处理器依赖）。
 * 具体实现可以是基于 Room、SQLite 或内存列表，在 data 层实现。
 */
@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: KnowledgeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBatch(entities: List<KnowledgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatch(entities: List<KnowledgeEntity>)

    @Transaction
    suspend fun upsertBatchTransactional(entities: List<KnowledgeEntity>) {
        upsertBatch(entities)
    }

    @Query("SELECT * FROM knowledge")
    suspend fun getAll(): List<KnowledgeEntity>

    @Query("SELECT * FROM knowledge WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KnowledgeEntity?

    @Query(
        "UPDATE knowledge SET " +
            "contentBlocksJson = :contentBlocksJson, " +
            "contentNormalized = :contentNormalized, " +
            "searchContent = :searchContent " +
            "WHERE id = :id"
    )
    suspend fun updateBlocksJsonAndSearchFields(
        id: Long,
        contentBlocksJson: String,
        contentNormalized: String,
        searchContent: String
    )

    @Query("SELECT * FROM knowledge WHERE contentNormalized = ''")
    suspend fun getEntriesMissingNormalized(): List<KnowledgeEntity>

    @Query("SELECT COUNT(1) FROM knowledge WHERE contentNormalized = ''")
    suspend fun countEntriesMissingNormalized(): Int

    @Query("UPDATE knowledge SET contentNormalized = :normalized, searchContent = :normalized WHERE id = :id")
    suspend fun updateNormalizedContent(id: Long, normalized: String)

    @Query(
        "SELECT * FROM knowledge " +
            "WHERE (CASE WHEN searchContent IS NULL OR searchContent = '' THEN content ELSE searchContent END) LIKE '%' || :keyword || '%' " +
            "OR title LIKE '%' || :keyword || '%' " +
            "OR source LIKE '%' || :keyword || '%'"
    )
    suspend fun searchByKeyword(keyword: String): List<KnowledgeEntity>

    @Update
    suspend fun update(entity: KnowledgeEntity)

    // FTS search using FTS table
    @Query("SELECT k.* FROM knowledge k JOIN knowledge_fts ON k.rowid = knowledge_fts.rowid WHERE knowledge_fts MATCH :query")
    suspend fun searchByFts(query: String): List<KnowledgeEntity>

    @Query("INSERT INTO knowledge_fts(knowledge_fts) VALUES('rebuild')")
    suspend fun rebuildFts()

    @Query("SELECT COUNT(1) FROM knowledge_fts")
    suspend fun countFts(): Int

    @Query("SELECT * FROM knowledge LIMIT :n")
    suspend fun getSample(n: Int): List<KnowledgeEntity>

    @Query("SELECT COUNT(1) FROM knowledge WHERE source LIKE :sourcePrefix || '%'")
    suspend fun countBySourcePrefix(sourcePrefix: String): Int

    @Query(
        "SELECT COUNT(1) FROM knowledge " +
            "WHERE source LIKE :sourcePrefix || '%' " +
            "AND REPLACE((CASE WHEN searchContent IS NULL OR searchContent = '' THEN content ELSE searchContent END), ' ', '') LIKE '%' || :keywordNoSpace || '%'"
    )
    suspend fun countMatchesBySourcePrefix(sourcePrefix: String, keywordNoSpace: String): Int

    @Query("SELECT * FROM knowledge WHERE source LIKE :sourcePrefix || '%' LIMIT :limit")
    suspend fun sampleBySourcePrefix(sourcePrefix: String, limit: Int): List<KnowledgeEntity>

    @Query(
        "SELECT * FROM knowledge " +
            "WHERE REPLACE((CASE WHEN searchContent IS NULL OR searchContent = '' THEN content ELSE searchContent END), ' ', '') LIKE '%' || :keywordNoSpace || '%' " +
            "OR REPLACE(title, ' ', '') LIKE '%' || :keywordNoSpace || '%' " +
            "OR REPLACE(source, ' ', '') LIKE '%' || :keywordNoSpace || '%'"
    )
    suspend fun searchByKeywordNoSpace(keywordNoSpace: String): List<KnowledgeEntity>

    @Query(
        "SELECT * FROM knowledge " +
            "WHERE (CASE WHEN searchContent IS NULL OR searchContent = '' THEN content ELSE searchContent END) LIKE :pattern ESCAPE '/' " +
            "OR title LIKE :pattern ESCAPE '/' " +
            "OR source LIKE :pattern ESCAPE '/'"
    )
    suspend fun searchByKeywordFuzzy(pattern: String): List<KnowledgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImportedFile(file: com.example.powerai.data.local.entity.ImportedFileEntity)

    @Query("SELECT COUNT(1) FROM imported_files WHERE fileId = :fileId")
    suspend fun importedFileExists(fileId: String): Int

    @Query("SELECT * FROM imported_files ORDER BY timestamp DESC")
    suspend fun getImportedFiles(): List<ImportedFileEntity>
}
