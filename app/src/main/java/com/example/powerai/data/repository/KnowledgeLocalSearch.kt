package com.example.powerai.data.repository

import android.util.Log
import com.example.powerai.BuildConfig
import com.example.powerai.data.importer.BlocksTextExtractor
import com.example.powerai.data.importer.TextSanitizer
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.database.DatabaseMigrationUtils
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.example.powerai.domain.model.KnowledgeItem

internal object KnowledgeLocalSearch {

    // When result set is large, block-json parsing becomes the bottleneck.
    // Limit block-level hit locating to keep local search responsive.
    private const val MAX_BLOCK_HIT_PROCESSING = 120

    @Volatile
    private var didOneTimeDiagnostics: Boolean = false

    @Volatile
    private var lastSnippetDiagnosticsQuery: String? = null

    @Volatile
    private var didOneTimeNormalizeSelfHeal: Boolean = false

    suspend fun searchLocal(
        dao: KnowledgeDao,
        query: String,
        entityToItem: (KnowledgeEntity) -> KnowledgeItem
    ): List<KnowledgeItem> {
        // Try FTS first, fall back to LIKE-based keyword search if FTS returns nothing.
        return try {
            if (!didOneTimeNormalizeSelfHeal) {
                didOneTimeNormalizeSelfHeal = true
                try {
                    if (BuildConfig.DEBUG) {
                        Log.d("KnowledgeRepository", "searchLocal: trigger one-time normalized self-heal")
                    }
                    DatabaseMigrationUtils.fillMissingNormalizedData(dao)
                } catch (_: Throwable) {
                }
            }

            val built = KnowledgeLocalSearchQueryBuilder.buildOrNull(query) ?: return emptyList()
            val q = built.raw
            val qNormalized = built.normalized
            val qNoSpace = built.noSpace
            val hasCjk = built.hasCjk

            val enableSnippetDiagnostics = BuildConfig.DEBUG &&
                qNormalized.isNotBlank() &&
                qNormalized.length <= 24 &&
                (hasCjk || qNormalized.any { it.isLetterOrDigit() }) &&
                (lastSnippetDiagnosticsQuery != qNormalized)
            if (enableSnippetDiagnostics) {
                lastSnippetDiagnosticsQuery = qNormalized
                Log.d("KB_SNIPPET_DIAG", "start query='$q' normalized='$qNormalized' noSpace='$qNoSpace' hasCjk=$hasCjk")
            }

            fun postProcess(entities: List<KnowledgeEntity>): List<KnowledgeItem> {
                data class BlockHit(val index: Int?, val id: String?, val snippetSource: String?)

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
                        // Fast path: avoid heavy block-json parsing for large result sets.
                        // IMPORTANT: some entries may match only via `contentNormalized` (built from blocks).
                        // If the displayed markdown content doesn't contain the query, fall back to normalized
                        // text so the preview snippet still includes the keyword and UI highlight stays visible.
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

                    // Some block schemas (or generic extraction) may produce a snippetSource that does NOT
                    // actually include the matching term (e.g. truncated/metadata-only). Ensure the preview
                    // contains the query so UI highlighting is always visible.
                    val candidate = hit.snippetSource?.takeIf { it.isNotBlank() }
                    val candidateMatches = candidate?.let { containsQuery(it, effectiveQuery) } ?: false

                    // Importer builds `contentNormalized` using `BlocksTextExtractor.extractPlainText`.
                    // Some schemas keep searchable text outside the top-level blocks array; use it as fallback
                    // if neither markdown content nor per-block candidate contains the query.
                    val blocksPlainText = if (!containsQuery(item.content, effectiveQuery) && !candidateMatches) {
                        try {
                            BlocksTextExtractor.extractPlainText(blocksJson).takeIf { it.isNotBlank() }
                        } catch (_: Throwable) {
                            null
                        }
                    } else {
                        null
                    }

                    // Prefer the stored markdown content for preview/snippets when it contains the query.
                    // This avoids block-meta noise (e.g. "b_xxx code markdown") and is more stable.
                    val textForSnippet = when {
                        containsQuery(item.content, effectiveQuery) -> item.content
                        candidate != null && candidateMatches -> candidate
                        blocksPlainText != null && containsQuery(blocksPlainText, effectiveQuery) -> blocksPlainText
                        else -> item.content
                    }

                    val snippet = KnowledgeSnippetBuilder.snippetAroundQuery(textForSnippet, effectiveQuery)
                    if (enableSnippetDiagnostics && idx < 40) {
                        val entityMatches = containsQuery(e.content, effectiveQuery)
                        val blocksPlainMatches = blocksPlainText?.let { containsQuery(it, effectiveQuery) } ?: false
                        val snippetMatches = containsQuery(snippet, effectiveQuery)
                        val used = when {
                            containsQuery(item.content, effectiveQuery) -> "content"
                            candidate != null && candidateMatches -> "candidate"
                            blocksPlainText != null && blocksPlainMatches -> "blocksPlain"
                            else -> "content"
                        }
                        if (entityMatches && !snippetMatches) {
                            Log.w(
                                "KB_SNIPPET_DIAG",
                                "MISSING_HIT used=$used id=${e.id} hitIndex=${hit.index} title='${e.title.take(60)}' snippet='${snippet.take(120)}'"
                            )
                        } else if (idx < 6) {
                            Log.d(
                                "KB_SNIPPET_DIAG",
                                "ok used=$used id=${e.id} hitIndex=${hit.index} entityMatches=$entityMatches candidateMatches=$candidateMatches snippetMatches=$snippetMatches title='${e.title.take(40)}'"
                            )
                        }
                    }

                    // Targeted diagnostics for tricky cases (e.g. "第11条" where the hit appears late).
                    // This prints even if the item is not among the first few results.
                    if (enableSnippetDiagnostics && (e.title.contains("第11条") || e.title.contains("第 11 条"))) {
                        try {
                            val rawQ = effectiveQuery.trim()
                            val contentHasRaw = item.content.contains(rawQ)
                            val candidateHasRaw = candidate?.contains(rawQ) ?: false
                            val blocksPlainHasRaw = blocksPlainText?.contains(rawQ) ?: false
                            val chosenHasRaw = textForSnippet.contains(rawQ)
                            val snippetHasRaw = snippet.contains(rawQ)
                            val contentIdx = if (rawQ.isNotBlank()) item.content.indexOf(rawQ) else -1
                            val chosenIdx = if (rawQ.isNotBlank()) textForSnippet.indexOf(rawQ) else -1
                            val snippetIdx = if (rawQ.isNotBlank()) snippet.indexOf(rawQ) else -1

                            Log.w(
                                "KB_SNIPPET_DIAG",
                                "TARGET_11 id=${e.id} hitIndex=${hit.index} usedCandidate=$candidateMatches usedBlocksPlain=${blocksPlainText != null} " +
                                    "len(content)=${item.content.length} len(candidate)=${candidate?.length ?: 0} len(blocksPlain)=${blocksPlainText?.length ?: 0} " +
                                    "rawIn(content)=$contentHasRaw rawIn(candidate)=$candidateHasRaw rawIn(blocksPlain)=$blocksPlainHasRaw rawIn(chosen)=$chosenHasRaw rawIn(snippet)=$snippetHasRaw " +
                                    "idx(content)=$contentIdx idx(chosen)=$chosenIdx idx(snippet)=$snippetIdx title='${e.title.take(60)}' " +
                                    "snippetTail='${snippet.takeLast(120)}'"
                            )
                        } catch (_: Throwable) {
                        }
                    }

                    return@mapIndexed item.copy(
                        content = snippet,
                        hitBlockIndex = hit.index,
                        hitBlockId = hit.id
                    )
                }
            }

            // NOTE: Avoid expensive diagnostics on every query; it makes local search feel slow.
            if (BuildConfig.DEBUG && !didOneTimeDiagnostics) {
                didOneTimeDiagnostics = true
                try {
                    val ftsRows = dao.countFts()
                    Log.d("SEARCH_CHECK", "ftsRowCount=$ftsRows")
                } catch (_: Throwable) {
                }
            }

            // CJK strategy: Prefer whitespace-insensitive LIKE first.
            // Rationale: FTS4(unicode61) does not do reliable substring matching for CJK inside long tokens
            // and PDF/OCR extraction may insert whitespace between characters (e.g. "回 路").
            if (hasCjk) {
                val like = try {
                    dao.searchByKeywordNoSpace(qNoSpace)
                } catch (t: Throwable) {
                    Log.w("KnowledgeRepository", "searchLocal: CJK searchByKeywordNoSpace threw", t)
                    emptyList()
                }
                if (like.isNotEmpty()) {
                    Log.d("KnowledgeRepository", "searchLocal: used LIKE(no-space) for CJK query='$q' count=${like.size}")
                    return postProcess(like)
                }
            }

            // Non-CJK strategy: try FTS first, fall back to LIKE.
            // Use normalized query for both FTS and LIKE since contentNormalized is aggressively cleaned.
            val ftsQuery = KnowledgeSearchSqlPatterns.buildFtsMatchQuery(qNormalized)
            val fts = try {
                dao.searchByFts(ftsQuery)
            } catch (t: Throwable) {
                Log.w("KnowledgeRepository", "searchLocal: searchByFts threw", t)
                emptyList()
            }
            if (fts.isNotEmpty()) {
                // For short CJK queries, FTS may match too broadly (tokenized into single characters).
                // Post-filter to require substring containment after whitespace-stripping.
                val shouldTighten = qNormalized.length in 1..4 && qNormalized.any { it.code in 0x4E00..0x9FFF }
                val filtered = if (shouldTighten) {
                    fts.filter { e ->
                        val queryNoSpace = qNormalized.replace(Regex("\\s+"), "")
                        val contentNoSpace = e.contentNormalized.replace(Regex("\\s+"), "")
                        val titleNoSpace = TextSanitizer.normalizeForSearch(e.title).replace(Regex("\\s+"), "")
                        val sourceNoSpace = TextSanitizer.normalizeForSearch(e.source).replace(Regex("\\s+"), "")
                        contentNoSpace.contains(queryNoSpace) ||
                            titleNoSpace.contains(queryNoSpace) ||
                            sourceNoSpace.contains(queryNoSpace)
                    }
                } else {
                    fts
                }

                if (filtered.isNotEmpty()) {
                    Log.d("KnowledgeRepository", "searchLocal: used FTS for query='$q' count=${filtered.size}")
                    return postProcess(filtered)
                }
            }

            Log.d("SEARCH_CHECK", "FTS empty, fallback to LIKE, q=$q normalized=$qNormalized ftsQuery=$ftsQuery")
            val like = try {
                dao.searchByKeywordNoSpace(qNoSpace)
            } catch (t: Throwable) {
                Log.w("KnowledgeRepository", "searchLocal: searchByKeywordNoSpace threw", t)
                emptyList()
            }
            Log.d("KnowledgeRepository", "searchLocal: FTS empty, used LIKE for query='$q' count=${like.size}")

            if (like.isNotEmpty()) {
                return postProcess(like)
            }

            // Last-resort: fuzzy LIKE for longer queries (e.g., %音%乐%教%师%).
            val fuzzyPattern = KnowledgeSearchSqlPatterns.buildFuzzyLikePattern(qNormalized)
            if (fuzzyPattern != null) {
                val fuzzy = try {
                    dao.searchByKeywordFuzzy(fuzzyPattern)
                } catch (t: Throwable) {
                    Log.w("KnowledgeRepository", "searchLocal: searchByKeywordFuzzy threw", t)
                    emptyList()
                }
                if (fuzzy.isNotEmpty()) {
                    Log.d("KnowledgeRepository", "searchLocal: used LIKE(fuzzy) for query='$q' count=${fuzzy.size}")
                    return postProcess(fuzzy)
                }
            }

            emptyList()
        } catch (t: Throwable) {
            // On any unexpected failure, fall back to keyword search
            Log.w("KnowledgeRepository", "searchLocal failed, falling back to keyword search", t)
            val qNoSpace = query.replace(Regex("\\s+"), "")
            val entities = try {
                val noSpace = dao.searchByKeywordNoSpace(qNoSpace)
                if (noSpace.isNotEmpty()) {
                    noSpace
                } else {
                    val fuzzyPattern = KnowledgeSearchSqlPatterns.buildFuzzyLikePattern(qNoSpace)
                    if (fuzzyPattern != null) dao.searchByKeywordFuzzy(fuzzyPattern) else dao.searchByKeyword(query)
                }
            } catch (_: Throwable) {
                dao.searchByKeyword(query)
            }
            entities.map { e ->
                val item = entityToItem(e)
                item.copy(content = KnowledgeSnippetBuilder.snippetAroundQuery(item.content, query))
            }
        }
    }
}
