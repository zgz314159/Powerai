package com.example.powerai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

	@Query("SELECT * FROM knowledge")
	suspend fun getAll(): List<KnowledgeEntity>

	@Query("SELECT * FROM knowledge WHERE content LIKE '%' || :keyword || '%' OR title LIKE '%' || :keyword || '%' OR source LIKE '%' || :keyword || '%'")
	suspend fun searchByKeyword(keyword: String): List<KnowledgeEntity>

	@Update
	suspend fun update(entity: KnowledgeEntity)

	// FTS search using FTS table
	@Query("SELECT k.* FROM knowledge k JOIN knowledge_fts ON k.rowid = knowledge_fts.rowid WHERE knowledge_fts MATCH :query")
	suspend fun searchByFts(query: String): List<KnowledgeEntity>

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertImportedFile(file: com.example.powerai.data.local.entity.ImportedFileEntity)

	@Query("SELECT COUNT(1) FROM imported_files WHERE fileId = :fileId")
	suspend fun importedFileExists(fileId: String): Int
}

