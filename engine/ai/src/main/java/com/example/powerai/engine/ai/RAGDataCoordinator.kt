package com.example.powerai.engine.ai

import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.repository.VectorRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * RAGDataCoordinator: 协调向量检索与知识条目共享，供多个引擎/页面复用
 *
 * 目标：在不修改底DB 结构下，复用 VectorRepository/KnowledgeRepository 的检索结果，
 * 并提供一个共享缓存与流用于流式处理（例如 DeepSeek thinking 回调）
 */
class RAGDataCoordinator(
    private val vectorRepo: VectorRepository,
    private val knowledgeRepo: KnowledgeRepository
) {
    // 用于流式推送思维链或实时检索片
    private val _stream = MutableSharedFlow<String>(replay = 0)
    val stream: SharedFlow<String> = _stream

    suspend fun retrieveByQuery(query: String, topK: Int = 5): List<KnowledgeItem> {
        // 使用已有的查询编码器/向量化链路由 domain 层负责产生向量并查询
        // 这里复用 VectorRepository search + KnowledgeRepository.getLocalItemById
        // 假设调用方先query 转换为向量（QueryEncoder）并调用 vectorRepo.search
        // 为保持不侵入底层结构，调用方可将 ids 列表传入本方法的重载
        throw UnsupportedOperationException("请使retrieveByIds(ids:LongArray) 或先生成向量后调vectorRepo.search")
    }

    suspend fun retrieveByIds(ids: LongArray): List<KnowledgeItem> {
        val res = mutableListOf<KnowledgeItem>()
        for (id in ids) {
            try {
                val item = knowledgeRepo.getLocalItemById(id)
                if (item != null) res.add(item)
            } catch (_: Throwable) {}
        }
        return res
    }

    suspend fun publishStreamFragment(fragment: String) {
        try {
            _stream.emit(fragment)
        } catch (_: Throwable) {}
    }
}
