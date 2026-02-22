package com.example.powerai.domain.repository

import com.example.powerai.domain.model.KnowledgeItem

/**
 * 定义电力知识相关的数据访问能力，领域层仅声明接口，不依赖具体实现或 Android API。
 */
interface KnowledgeRepository {
    /** 在本地知识库中根据关键词搜索匹配条目 */
    suspend fun searchLocal(query: String): List<KnowledgeItem>

    /**
     * 导入外部文档。
     * 参数使用字符串形式的 URI（例如 SAF content URI 的字符串表示），以避免领域层依赖 Android 框架类。
     */
    suspend fun importDocuments(uris: List<String>): Result<Unit>

    // 批量插入领域模型条目
    suspend fun insertBatch(items: List<KnowledgeItem>)

    // 文件去重：检查并标记已导入文件
    suspend fun isFileImported(fileId: String): Boolean

    suspend fun markFileImported(fileId: String, fileName: String, timestamp: Long, status: String)
}
