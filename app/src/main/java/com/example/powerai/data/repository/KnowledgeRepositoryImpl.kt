package com.example.powerai.data.repository

import android.content.Context
import android.net.Uri
import com.example.powerai.data.importer.TextSanitizer
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.repository.KnowledgeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import javax.inject.Inject

/**
 * 简单的仓库实现：负责在 DAO 与 DocumentImportManager 之间进行调用并完成实体与领域模型的映射。
 */
class KnowledgeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: KnowledgeDao
) : KnowledgeRepository {
    /**
     * 导入文件：SAF → 文本 → KnowledgeEntity → Room
     */
    suspend fun importDocument(context: android.content.Context, uri: android.net.Uri, title: String, source: String) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open uri")
        val content = input.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
        val entity = com.example.powerai.data.local.entity.KnowledgeEntity(title = title, content = content, source = source)
        dao.insert(entity)
    }

    private fun entityToItem(e: KnowledgeEntity): KnowledgeItem = KnowledgeItem(
        id = e.id,
        title = e.title,
        content = e.content,
        source = e.source,
        pageNumber = e.pageNumber,
        category = e.category,
        keywords = if (e.keywordsSerialized.isBlank()) emptyList() else e.keywordsSerialized.split(',').map { it.trim() }
    )

    /**
     * 本地搜索：返回领域模型列表（实现接口）
     */
    override suspend fun searchLocal(query: String): List<KnowledgeItem> {
        return KnowledgeLocalSearch.searchLocal(
            dao = dao,
            query = query,
            entityToItem = ::entityToItem
        )
    }

    /**
     * 批量导入实现：接收字符串形式的 URI 列表（例如 SAF content://...），
     * 在 Data 层使用注入的 Application Context 读取并写入数据库。
     */
    override suspend fun importDocuments(uris: List<String>): Result<Unit> {
        try {
            for (s in uris) {
                val uri = Uri.parse(s)
                val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open uri")
                val content = input.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
                val title = uri.lastPathSegment ?: "imported"
                val entity = KnowledgeEntity(title = title, content = content, source = s)
                dao.insert(entity)
            }
            return Result.success(Unit)
        } catch (t: Throwable) {
            return Result.failure(t)
        }
    }

    override suspend fun insertBatch(items: List<com.example.powerai.domain.model.KnowledgeItem>) {
        val entities = items.map { item ->
            val normalized = TextSanitizer.normalizeForSearch(item.content)
            KnowledgeEntity(
                title = item.title,
                content = item.content,
                source = item.source,
                category = item.category,
                keywordsSerialized = item.keywords.joinToString(","),
                contentNormalized = normalized,
                searchContent = normalized
            )
        }
        dao.insertBatch(entities)
    }

    override suspend fun isFileImported(fileId: String): Boolean {
        return dao.importedFileExists(fileId) > 0
    }

    override suspend fun markFileImported(fileId: String, fileName: String, timestamp: Long, status: String) {
        dao.insertImportedFile(com.example.powerai.data.local.entity.ImportedFileEntity(fileId = fileId, fileName = fileName, timestamp = timestamp, status = status))
    }

    /**
     * 获取全部知识条目
     */
    suspend fun getAll(): List<com.example.powerai.data.local.entity.KnowledgeEntity> = dao.getAll()
}
