package com.example.powerai.domain.model

/**
 * 表示一条电力线路工的知识条目（例如法规段落、操作规范、检查要点）。
 * 该类仅为纯数据结构，不包含任何业务或 Android 相关依赖。
 */
data class KnowledgeItem(
	/** 唯一标识符，数据库或导入时的主键 */
	val id: Long,

	/** 条款或知识点的标题 */
	val title: String,

	/** 条款的详细内容或说明文本 */
	val content: String,

	/** 原始来源文件名或来源描述（如手册名、页码） */
	val source: String,

	/** 分类标签，例如：安规 / 架空线路 / 电缆 */
	val category: String,

	/** 若干关键词，便于索引与搜索 */
	val keywords: List<String>
)
