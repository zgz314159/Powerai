package com.example.powerai.core.data.repository

import com.example.powerai.core.model.util.BlocksTextExtractor
import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.model.KnowledgeItem
import org.json.JSONArray

/**
 * Helper object containing logic originally nested inside [KnowledgeLocalSearch].
 *
 * Contains the "postProcess" function that applies snippet generation, block
 * hit detection, and other heuristics to raw database entities after the initial
 * query has returned a result set. Extracting this code makes the core search
 * path easier to read and allows further splitting in the future.
 */
object KnowledgeLocalSearchProcessor {
    // When result set is large, block-json parsing becomes the bottleneck.
    // Limit block-level hit locating to keep local search responsive.
    private const val MAX_BLOCK_HIT_PROCESSING = 120

    data class BlockHit(val index: Int?, val id: String?, val snippetSource: String?)

    fun postProcess(
        entities: List<KnowledgeEntity>,
        built: KnowledgeLocalSearchQuery,
        entityToItem: (KnowledgeEntity) -> KnowledgeItem
    ): List<KnowledgeItem> {
        val qNormalized = built.normalized
        val qNoSpace = built.noSpace
        val hasCjk = built.hasCjk

        fun containsQuery(text: String, normalizedQuery: String): Boolean {
            val qNorm = TextSanitizer.normalizeForSearch(normalizedQuery).lowercase()
            if (qNorm.isBlank()) return false
            val qNoSpace = qNorm.replace(Regex("\\s+"), "")

            val tNorm = TextSanitizer.normalizeForSearch(text).lowercase()
            if (tNorm.contains(qNorm)) return true
            if (qNoSpace.isNotBlank()) {
                val tNoSpace = tNorm.replace(Regex("\\s+"), "")
                if (tNoSpace.contains(qNoSpace)) return true
            }
            return false
        }

        fun computeBlockHit(blocksJson: String, normalizedQuery: String): BlockHit {
            val qNorm = TextSanitizer.normalizeForSearch(normalizedQuery).lowercase()
            if (qNorm.isBlank()) return BlockHit(index = null, id = null, snippetSource = null)
            val queryNoSpace = qNorm.replace(Regex("\\s+"), "")

            val texts = BlocksTextExtractor.extractBlockPlainTexts(blocksJson)
            if (texts.isEmpty()) return BlockHit(index = null, id = null, snippetSource = null)

            var hitIndex: Int? = null
            for ((idx, t) in texts.withIndex()) {
                if (t.isBlank()) continue
                val tNorm = TextSanitizer.normalizeForSearch(t).lowercase()
                if (tNorm.contains(qNorm)) {
                    hitIndex = idx
                    break
                }
                if (queryNoSpace.isNotBlank()) {
                    val tNoSpace = tNorm.replace(Regex("\\s+"), "")
                    if (tNoSpace.contains(queryNoSpace)) {
                        hitIndex = idx
                        break
                    }
                }
            }

            val hitId = if (hitIndex != null) {
                try {
                    BlocksTextExtractor.computeStableBlockIds(blocksJson).getOrNull(hitIndex)
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }

            val snippetSource = when {
                hitIndex != null -> texts.getOrNull(hitIndex)
                else -> texts.joinToString("\n")
            }
            return BlockHit(index = hitIndex, id = hitId, snippetSource = snippetSource)
        }

        return entities.mapIndexed { idx, e ->
            val item = entityToItem(e)
            val effectiveQuery = qNormalized

            val blocksJson = e.contentBlocksJson?.takeIf { it.isNotBlank() }
            if (blocksJson == null || idx >= MAX_BLOCK_HIT_PROCESSING) {
                val textForSnippet = if (containsQuery(item.content, effectiveQuery)) {
                    item.content
                } else {
                    e.contentNormalized.takeIf { it.isNotBlank() } ?: item.content
                }
                return@mapIndexed item.copy(
                    content = KnowledgeSnippetBuilder.snippetAroundQuery(textForSnippet, effectiveQuery),
                    hitBlockIndex = null,
                    hitBlockId = null
                )
            }

            val hit = try {
                computeBlockHit(blocksJson, effectiveQuery)
            } catch (_: Throwable) {
                BlockHit(index = null, id = null, snippetSource = null)
            }

            val candidate = hit.snippetSource?.takeIf { it.isNotBlank() }
            val candidateMatches = candidate?.let { containsQuery(it, effectiveQuery) } ?: false

            val blocksPlainText = if (!containsQuery(item.content, effectiveQuery) && !candidateMatches) {
                try {
                    BlocksTextExtractor.extractPlainText(blocksJson).takeIf { it.isNotBlank() }
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }

            val textForSnippet = when {
                containsQuery(item.content, effectiveQuery) -> item.content
                candidate != null && candidateMatches -> candidate
                blocksPlainText != null && containsQuery(blocksPlainText, effectiveQuery) -> blocksPlainText
                else -> item.content
            }

            val snippet = KnowledgeSnippetBuilder.snippetAroundQuery(textForSnippet, effectiveQuery)
            item.copy(content = snippet, hitBlockIndex = hit.index, hitBlockId = hit.id)
        }
    }
}
