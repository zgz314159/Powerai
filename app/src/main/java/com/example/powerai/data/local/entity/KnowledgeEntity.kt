package com.example.powerai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 数据库中存储的知识条目实体。
 */
@Entity(tableName = "knowledge")
data class KnowledgeEntity(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0L,
	val title: String,
	val content: String,
	val source: String,
	val category: String = "",
	val keywordsSerialized: String = ""
)

