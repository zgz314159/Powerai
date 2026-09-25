package com.example.powerai.core.repository

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.LocalHighlightTarget
import com.example.powerai.core.model.ImportedFile

/**
 * 定义电力知识相关的数据访问能力
 */
interface KnowledgeRepository {
    /** 在本地知识库中根据关键词搜索匹配条目 */
    suspend fun searchLocal(query: String): List<KnowledgeItem>

    /** 根据本地知识条目 id 获取完整条目 */
    suspend fun getLocalItemById(id: Long): KnowledgeItem?

    /** 导入外部文档 */
    suspend fun importDocuments(uris: List<String>): Result<Unit>

    /** 批量插入领域模型条目 */
    suspend fun insertBatch(items: List<KnowledgeItem>)

    /** 文件去重：检查并标记已导入文*/
    suspend fun isFileImported(fileId: String): Boolean

    suspend fun markFileImported(fileId: String, fileName: String, timestamp: Long, status: String)

    /** Resolve a more precise block-level highlight target */
    suspend fun resolveHighlightTarget(itemId: Long, highlight: String): LocalHighlightTarget?

    /** Count knowledge items associated with a specific PDF page */
    suspend fun countKnowledgeByPage(fileId: String, page: Int): Int

    /** Get knowledge items associated with a specific PDF page */
    suspend fun getItemsByPage(fileId: String, page: Int): List<KnowledgeItem>

    /** Get all imported files */
    suspend fun getImportedFiles(): List<ImportedFile>

    /** Get all knowledge items */
    suspend fun getAll(): List<KnowledgeItem>

    /** Search knowledge items by keyword in content */
    suspend fun searchByKeyword(query: String): List<KnowledgeItem>
}
