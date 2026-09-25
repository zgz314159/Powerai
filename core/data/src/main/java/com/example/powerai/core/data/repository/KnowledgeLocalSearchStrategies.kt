package com.example.powerai.core.data.repository

import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal interface LocalSearchStrategy {
    /**
     * Perform DAO queries for the given built query. Returns list of entities
     * (possibly empty) but never null. The caller may combine results from
     * multiple strategies if desired.
     */
    suspend fun search(
        dao: KnowledgeDao,
        built: KnowledgeLocalSearchQuery
    ): List<KnowledgeEntity>
}

object CjkSearchStrategy : LocalSearchStrategy {
    override suspend fun search(
        dao: KnowledgeDao,
        built: KnowledgeLocalSearchQuery
    ): List<KnowledgeEntity> {
        // only used when hasCjk==true; try no-space LIKE first
        val qNoSpace = built.noSpace
        return try {
            KnowledgeLocalSearch.traceQuery("LIKE_NO_SPACE", qNoSpace) { dao.searchByKeywordNoSpace(qNoSpace) }
        } catch (t: Throwable) {
            // log removed: CJK searchByKeywordNoSpace threw
            emptyList()
        }
    }
}

object GenericSearchStrategy : LocalSearchStrategy {
    override suspend fun search(
        dao: KnowledgeDao,
        built: KnowledgeLocalSearchQuery
    ): List<KnowledgeEntity> {
        // execute FTS MATCH and brute-force LIKE in parallel, then merge distinct
        val q = built.raw
        val qNormalized = built.normalized
        val ftsQuery = KnowledgeSearchSqlPatterns.buildFtsMatchQuery(qNormalized)
        return coroutineScope {
            val ftsDeferred = async {
                try {
                    KnowledgeLocalSearch.traceQuery("FTS", ftsQuery) { dao.searchByFts(ftsQuery) }
                } catch (t: Throwable) {
                    // log removed: searchLocal: searchByFts threw
                    emptyList<KnowledgeEntity>()
                }
            }
            val likeDeferred = async {
                try {
                    val results = KnowledgeLocalSearch.traceQuery("LIKE_INTERNAL", q) { dao.searchByLikeInternal(q) }
                    // log removed: LIKE Search executed for query found count
                    results
                } catch (t: Throwable) {
                    // log removed: searchByLikeInternal threw
                    emptyList<KnowledgeEntity>()
                }
            }
            val fts = try { ftsDeferred.await() } catch (_: Throwable) { emptyList() }
            val like = try { likeDeferred.await() } catch (_: Throwable) { emptyList() }
            (fts + like).distinctBy { it.id }
        }.let { merged ->
            if (merged.isNotEmpty()) return merged
            // last-resort fuzzy LIKE
            val fuzzyPattern = KnowledgeSearchSqlPatterns.buildFuzzyLikePattern(qNormalized)
            if (fuzzyPattern != null) {
                try {
                    val fuzzy = KnowledgeLocalSearch.traceQuery("LIKE_FUZZY", fuzzyPattern) { dao.searchByKeywordFuzzy(fuzzyPattern) }
                    if (fuzzy.isNotEmpty()) return fuzzy
                } catch (t: Throwable) {
                    // log removed: searchByKeywordFuzzy threw
                }
            }
            emptyList()
        }
    }
}
