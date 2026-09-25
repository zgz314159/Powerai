package com.example.powerai.data.repository

import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.repository.KnowledgeSearchSqlPatterns

import com.example.powerai.core.model.KnowledgeItem

import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.domain.retrieval.FtsRetriever
import com.example.powerai.domain.util.SemanticRoleSearchTextBuilder
import com.example.powerai.core.model.util.TextSanitizer
import javax.inject.Inject

/** Adapter that converts DAO FTS results into `RetrievalResult`. */
class RoomFtsRetriever @Inject constructor(private val dao: KnowledgeDao) : FtsRetriever {
    private val lowSignalTokens = setOf("什", "情况", "情况", "哪些", "何种", "情形", "条件", "")

    override suspend fun search(query: String, k: Int): List<RetrievalResult> {
        val normalized = TextSanitizer.normalizeForSearch(query)
        val ftsQuery = KnowledgeSearchSqlPatterns.buildFtsMatchQuery(normalized)
        if (normalized.isBlank() || ftsQuery.isBlank()) return emptyList()

        val ftsEntities = try {
            dao.searchByFts(ftsQuery)
        } catch (t: Throwable) {
            throw t
        }

        val combined = mutableListOf<com.example.powerai.core.data.entity.KnowledgeEntity>()
        combined.addAll(ftsEntities)
        if (combined.size < k) {
            try {
                val kws = dao.searchByKeyword(normalized)
                for (e in kws) {
                    if (combined.none { it.id == e.id }) {
                        combined.add(e)
                    }
                    if (combined.size >= k) break
                }
            } catch (_: Throwable) {
            }
        }
        if (combined.size < k) {
            val noSpace = normalized.replace(" ", "")
            if (noSpace.isNotBlank() && noSpace != normalized) {
                try {
                    val noSpaceHits = dao.searchByKeywordNoSpace(noSpace)
                    for (e in noSpaceHits) {
                        if (combined.none { it.id == e.id }) {
                            combined.add(e)
                        }
                        if (combined.size >= k) break
                    }
                } catch (_: Throwable) {
                }
            }
        }
        if (combined.size < k) {
            val tokenHits = multiTokenCoverageFallback(normalized, k)
            for (e in tokenHits) {
                if (combined.none { it.id == e.id }) {
                    combined.add(e)
                }
                if (combined.size >= k) break
            }
        }
        return combined.take(k).mapIndexed { idx, e ->
            val profile = SemanticRoleSearchTextBuilder.analyze(
                blocksJson = e.contentBlocksJson,
                fallbackPlainText = e.contentNormalized.ifBlank { e.content },
                query = query
            )
            var base = 1.0f - (idx.toFloat() / (combined.size.coerceAtLeast(1)))
            if (profile.bodyCount > 0 || profile.headingCount > 0 || profile.tableCount > 0) base += 0.05f
            if (profile.figureNodeCount > 0) base += 0.03f
            if (profile.lowValueOnly) base -= 0.16f
            if (profile.figureLabelMatchCount > 0) base += 0.16f
            if (profile.figureCaptionMatchCount > 0) base += 0.08f
            when (profile.dominantRole) {
                "caption" -> base -= 0.08f
                "artifact", "figure" -> base -= 0.12f
                "heading", "body", "table" -> base += 0.04f
                "figure_node" -> base += 0.03f
            }
            val meta = mapOf(
                "title" to e.title,
                "snippet" to e.content,
                "source" to e.source,
                "category" to e.category,
                "semantic_dominant_role" to profile.dominantRole,
                "semantic_low_value_only" to profile.lowValueOnly.toString(),
                "semantic_figure_node_count" to profile.figureNodeCount.toString(),
                "semantic_figure_label_matches" to profile.figureLabelMatchCount.toString(),
                "semantic_figure_caption_matches" to profile.figureCaptionMatchCount.toString(),
                "semantic_matched_figure_label" to profile.matchedFigureLabel,
                "semantic_matched_figure_caption" to profile.matchedFigureCaption,
                "semantic_matched_canonical_figure_node_id" to profile.matchedCanonicalFigureNodeId
            )
            val item = KnowledgeItem(
                id = e.id,
                title = e.title,
                content = e.content,
                source = e.source,
                pageNumber = e.pageNumber,
                category = e.category,
                keywords = if (e.keywordsSerialized.isBlank()) emptyList() else e.keywordsSerialized.split(',').map { it.trim() },
                highlightHint = profile.matchedFigureLabel.ifBlank { profile.matchedFigureCaption.takeIf { it.isNotBlank() } },
                contentBlocksJson = e.contentBlocksJson
            )
            RetrievalResult(
                id = e.id,
                score = base.coerceIn(0f, 1f),
                confidence = null,
                source = "fts",
                metadata = meta,
                debug = mapOf(
                    "semantic_dominant_role" to profile.dominantRole,
                    "semantic_low_value_only" to profile.lowValueOnly,
                    "semantic_body_blocks" to profile.bodyCount,
                    "semantic_heading_blocks" to profile.headingCount,
                    "semantic_table_blocks" to profile.tableCount,
                    "semantic_caption_blocks" to profile.captionCount,
                    "semantic_artifact_blocks" to profile.artifactCount,
                    "semantic_figure_blocks" to profile.figureCount,
                    "semantic_figure_node_count" to profile.figureNodeCount,
                    "semantic_figure_label_matches" to profile.figureLabelMatchCount,
                    "semantic_figure_caption_matches" to profile.figureCaptionMatchCount,
                    "semantic_matched_figure_label" to profile.matchedFigureLabel,
                    "semantic_matched_figure_caption" to profile.matchedFigureCaption,
                    "semantic_matched_canonical_figure_node_id" to profile.matchedCanonicalFigureNodeId
                ),
                item = item
            )
        }
    }

    private suspend fun multiTokenCoverageFallback(
        normalized: String,
        k: Int
    ): List<com.example.powerai.core.data.entity.KnowledgeEntity> {
        val tokens = normalized
            .split(' ')
            .filter { token -> token.length >= 2 && token !in lowSignalTokens }
            .distinct()

        if (tokens.size < 2) return emptyList()

        val scoreById = linkedMapOf<Long, Int>()
        val entityById = linkedMapOf<Long, com.example.powerai.core.data.entity.KnowledgeEntity>()

        for (token in tokens) {
            val hits = try {
                dao.searchByKeywordInContent(token)
            } catch (_: Throwable) {
                try {
                    dao.searchByKeyword(token)
                } catch (_: Throwable) {
                    emptyList()
                }
            }
            for (entity in hits) {
                scoreById[entity.id] = (scoreById[entity.id] ?: 0) + 1
                entityById.putIfAbsent(entity.id, entity)
            }
        }

        return scoreById.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .mapNotNull { entityById[it.key] }
            .take(k)
    }
}
