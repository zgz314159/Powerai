package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.repository.KnowledgeRepository
import javax.inject.Inject

/**
 * 本用例仅负责根据关键词从本地知识库检索匹配的知识条目。
 * 不在此层处理异常或 UI 状态，异常应由调用方处理。
 */
class LocalSearchUseCase @Inject constructor(private val repository: KnowledgeRepository) {
	/**
	 * 根据关键词查询本地知识并返回匹配的条目列表（领域模型）
	 */
	suspend fun invoke(keyword: String): List<KnowledgeItem> {
		return repository.searchLocal(keyword)
	}
}
