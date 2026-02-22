package com.example.powerai.data.repository

import com.example.powerai.data.remote.VectorSearchClient
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.repository.KnowledgeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class VectorSearchRepository @Inject constructor(
    private val localRepo: KnowledgeRepositoryImpl,
    private val vectorClient: VectorSearchClient
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

        // Local yields nothing: fall back to vector top-k.
        val mapped = try {
            vectorClient.search(query = query, limit = 5).mapNotNull { mapToItem(it) }
        } catch (_: Throwable) {
            emptyList()
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
            title = if (unitName.isNotBlank()) unitName else "向量检索结果",
            content = contentMarkdown,
            source = source,
            pageNumber = null,
            category = "VECTOR",
            keywords = emptyList()
        )
    }
}
