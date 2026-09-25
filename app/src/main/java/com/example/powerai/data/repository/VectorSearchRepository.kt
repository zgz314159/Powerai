package com.example.powerai.data.repository

import com.example.powerai.core.data.dao.KnowledgeDao

import com.example.powerai.core.data.repository.KnowledgeRepositoryImpl

import com.example.powerai.core.repository.KnowledgeRepository

import com.example.powerai.core.model.KnowledgeItem

import com.example.powerai.domain.retrieval.HybridRetrievalService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VectorSearchRepository @Inject constructor(
    private val localRepo: KnowledgeRepositoryImpl,
    private val hybridService: HybridRetrievalService,
    private val knowledgeDao: KnowledgeDao
) : KnowledgeRepository by localRepo {

    override suspend fun searchLocal(query: String): List<KnowledgeItem> {
        val local = localRepo.searchLocal(query)
        if (local.isNotEmpty()) return local

        val hits = try {
            hybridService.retrieveHybrid(query, 5)
        } catch (_: Throwable) { emptyList() }

        val items = mutableListOf<KnowledgeItem>()
        for (rr in hits) {
            val item = rr.item
            if (item != null) {
                items.add(item)
                continue
            }

            val id = rr.id ?: continue
            try {
                val entity = knowledgeDao.getById(id)
                if (entity != null && entity.content.isNotBlank()) {
                    items.add(
                        KnowledgeItem(
                            id = entity.id,
                            title = "[AI][Semantic] " + entity.title.ifBlank { "向量检索结" },
                            content = entity.content,
                            source = entity.source,
                            pageNumber = entity.pageNumber,
                            category = entity.category.ifBlank { "VECTOR" },
                            keywords = if (entity.keywordsSerialized.isBlank()) emptyList() else entity.keywordsSerialized.split(',')
                        )
                    )
                }
            } catch (_: Throwable) {
            }
        }

        return items
    }
}
