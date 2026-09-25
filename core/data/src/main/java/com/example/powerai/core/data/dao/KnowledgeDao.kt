package com.example.powerai.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.entity.KnowledgeListItemEntity
import com.example.powerai.core.data.entity.KnowledgeRowPayloadStat

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

    @Query(
        "SELECT id, title, substr(content, 1, 4000) AS content, source, category, pageNumber, sortOrder, imageUris " +
            "FROM knowledge ORDER BY id ASC"
    )
    suspend fun getAllForDatabaseList(): List<KnowledgeListItemEntity>

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

    @Query(
        "SELECT * FROM knowledge " +
            "WHERE (CASE WHEN searchContent IS NULL OR searchContent = '' THEN content ELSE searchContent END) LIKE '%' || :keyword || '%'"
    )
    suspend fun searchByKeywordInContent(keyword: String): List<KnowledgeEntity>

    @Query(
        "SELECT id, title, substr(content, 1, 4000) AS content, source, category, pageNumber, sortOrder, imageUris FROM knowledge " +
            "WHERE (CASE WHEN searchContent IS NULL OR searchContent = '' THEN content ELSE searchContent END) LIKE '%' || :keyword || '%'"
    )
    suspend fun searchByKeywordInContentForDatabase(keyword: String): List<KnowledgeListItemEntity>

    @Query("SELECT * FROM knowledge WHERE content LIKE '%' || :raw || '%' OR title LIKE '%' || :raw || '%' LIMIT 15")
    suspend fun searchByLikeInternal(raw: String): List<KnowledgeEntity>

    @Update
    suspend fun update(entity: KnowledgeEntity)

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
            "WHERE REPLACE((CASE WHEN searchContent IS NULL OR searchContent = '' THEN content ELSE searchContent END), ' ', '') LIKE '%' || :keywordNoSpace || '%'"
    )
    suspend fun searchByKeywordNoSpaceInContent(keywordNoSpace: String): List<KnowledgeEntity>

    @Query(
        "SELECT id, title, substr(content, 1, 4000) AS content, source, category, pageNumber, sortOrder, imageUris FROM knowledge " +
            "WHERE REPLACE((CASE WHEN searchContent IS NULL OR searchContent = '' THEN content ELSE searchContent END), ' ', '') LIKE '%' || :keywordNoSpace || '%'"
    )
    suspend fun searchByKeywordNoSpaceInContentForDatabase(keywordNoSpace: String): List<KnowledgeListItemEntity>

    @Query(
        "SELECT id, source, " +
            "COALESCE(LENGTH(content), 0) AS contentLength, " +
            "COALESCE(LENGTH(contentBlocksJson), 0) AS contentBlocksLength, " +
            "COALESCE(LENGTH(bboxJson), 0) AS bboxLength, " +
            "COALESCE(LENGTH(imageUris), 0) AS imageUrisLength " +
            "FROM knowledge " +
            "ORDER BY COALESCE(LENGTH(contentBlocksJson), 0) DESC, " +
            "COALESCE(LENGTH(content), 0) DESC, " +
            "COALESCE(LENGTH(imageUris), 0) DESC, id ASC LIMIT :limit"
    )
    suspend fun getLargestKnowledgeRows(limit: Int): List<KnowledgeRowPayloadStat>

    @Query(
        "SELECT * FROM knowledge " +
            "WHERE (CASE WHEN searchContent IS NULL OR searchContent = '' THEN content ELSE searchContent END) LIKE :pattern ESCAPE '/' " +
            "OR title LIKE :pattern ESCAPE '/' " +
            "OR source LIKE :pattern ESCAPE '/'"
    )
    suspend fun searchByKeywordFuzzy(pattern: String): List<KnowledgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedFile(file: ImportedFileEntity)

    @Query("SELECT COUNT(1) FROM imported_files WHERE fileId = :fileId")
    suspend fun importedFileExists(fileId: String): Int

    @Query("SELECT status FROM imported_files WHERE fileId = :fileId LIMIT 1")
    suspend fun getImportedFileStatus(fileId: String): String?

    @Query("SELECT * FROM imported_files ORDER BY timestamp DESC")
    suspend fun getImportedFiles(): List<ImportedFileEntity>

    @Query("SELECT COUNT(1) FROM knowledge WHERE source LIKE '%' || :fileId || '%' AND pageNumber = :page")
    suspend fun countByPage(fileId: String, page: Int): Int

    @Query("SELECT * FROM knowledge WHERE source LIKE '%' || :fileId || '%' AND pageNumber = :page ORDER BY id ASC")
    suspend fun getByPage(fileId: String, page: Int): List<KnowledgeEntity>
}
