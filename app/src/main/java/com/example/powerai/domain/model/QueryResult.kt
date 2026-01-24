package com.example.powerai.domain.model

/**
 * 表示一次查询（本地或 AI）的统一返回结构。
 * 仅为数据载体，不包含实现逻辑。
 */
data class QueryResult(
	/** 最终生成给用户的文本回答 */
	val answer: String,

	/** 本次查询引用到的知识条目列表（可为空） */
	val references: List<KnowledgeItem>,

	/** 对回答置信度的估计，范围 0.0 ~ 1.0 */
	val confidence: Float
)
