package com.example.powerai.data.repository

import com.example.powerai.data.remote.VectorSearchClient
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.domain.retriever.AnnRetriever
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.repository.KnowledgeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class VectorSearchRepository @Inject constructor(
    private val localRepo: KnowledgeRepositoryImpl,
    private val vectorClient: VectorSearchClient,
    private val knowledgeDao: KnowledgeDao,
    private val annRetriever: AnnRetriever
) : KnowledgeRepository by localRepo {

    override suspend fun searchLocal(query: String): List<KnowledgeItem> {
        // IMPORTANT: "searchLocal" must never hide newly-imported local content.
        // Vector index may lag behind DB imports, so local DB search must be the primary source.
        val local = localRepo.searchLocal(query)
        if (local.isNotEmpty()) {
            // Optionally enrich with a few vector hits (non-blocking best-effort).
            val mapped = try {
                vectorClient.search(query = query, limit = 5).mapNotNull { mapToItem(it) }
            } catch (_: Throwable) {
                emptyList()
            }
            return if (mapped.isEmpty()) local else local + mapped
        }

        // Local yields nothing: fall back to vector top-k. Try remote ANN retriever first,
        // map returned ids back to local KnowledgeEntity rows. This is best-effort
        // and non-blocking for the UI — on failure we return empty list.
        val mapped = try {
            val ids = annRetriever.search(query, 5)
            val items = mutableListOf<KnowledgeItem>()
            for (id in ids) {
                try {
                    val entity = knowledgeDao.getById(id)
                    if (entity != null && entity.content.isNotBlank()) {
                        items.add(
                            KnowledgeItem(
                                id = entity.id,
                                title = "[AI][Semantic] " + entity.title.ifBlank { "向量检索结果" },
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
            items
        } catch (_: Throwable) {
            // Fallback to existing remote vectorClient mapping if ANN retriever not available
            try {
                vectorClient.search(query = query, limit = 5).mapNotNull { mapToItem(it) }
            } catch (_: Throwable) {
                emptyList()
            }
        }
        return mapped
    }

    private fun mapToItem(hit: com.example.powerai.data.remote.dto.VectorSearchHit): KnowledgeItem? {
        val unitName = hit.unitName?.trim().orEmpty()
        val jobTitle = hit.jobTitle?.trim().orEmpty()
        val sourceFile = hit.sourceFile?.trim().orEmpty()
        val contentMarkdown = hit.contentMarkdown?.trim().orEmpty()

        if (contentMarkdown.isBlank()) return null

        // Approximate mapping requested:
        // - unitName -> title
        // - jobTitle -> (subTitle) -> stored in `source`
        // - source_file -> (path) -> appended into `source`
        val source = when {
            jobTitle.isNotBlank() && sourceFile.isNotBlank() -> "$jobTitle | $sourceFile"
            jobTitle.isNotBlank() -> jobTitle
            sourceFile.isNotBlank() -> sourceFile
            else -> "VectorSearch"
        }

        val stableKey = "$unitName|$jobTitle|$sourceFile"
        val id = -abs(stableKey.hashCode().toLong()) - 1L

        return KnowledgeItem(
            id = id,
            title = "[AI][Semantic] " + if (unitName.isNotBlank()) unitName else "向量检索结果",
            content = contentMarkdown,
            source = source,
            pageNumber = null,
            category = "VECTOR",
            keywords = emptyList()
        )
    }
}
