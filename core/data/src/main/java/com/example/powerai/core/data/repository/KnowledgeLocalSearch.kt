package com.example.powerai.core.data.repository

// TODO: 本地搜索实现曾经较长；索引构建、snippet/merge、查询策略均已脱出
//       KnowledgeLocalSearchProcessor LocalSearchStrategy 使主体方法瘦身
//       诊断/自愈逻辑已移动到 LocalSearchDiagnostics 对象
//       未来可进一步拆分策略或将查询过程公开为可配置组件

// android.util.Log removed per TODO order; debug traces were for local search diagnostics
import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.model.KnowledgeItem

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal object KnowledgeLocalSearch {

    // When result set is large, block-json parsing becomes the bottleneck.
    // Limit block-level hit locating to keep local search responsive.
    private const val MAX_BLOCK_HIT_PROCESSING = 120

    // Helper to log SQL-like query and memory usage around DAO invocations
    internal suspend fun <T> traceQuery(tag: String, query: String, block: suspend () -> List<T>): List<T> {
        val rt = Runtime.getRuntime()
        val before = rt.freeMemory()
        try { /* log removed SQL_TRACE before query */ } catch (_: Throwable) {}
        val result = try { block() } catch (t: Throwable) {
            try { /* log removed SQL_TRACE query exception */ } catch (_: Throwable) {}
            throw t
        }
        val after = rt.freeMemory()
        try { /* log removed SQL_TRACE after query */ } catch (_: Throwable) {}
        return result
    }


    suspend fun searchLocal(
        dao: KnowledgeDao,
        query: String,
        entityToItem: (KnowledgeEntity) -> KnowledgeItem
    ): List<KnowledgeItem> {
        // Try FTS first, fall back to LIKE-based keyword search if FTS returns nothing.
        return try {
            // perform one–time maintenance and diagnostics via helper
            LocalSearchDiagnostics.ensureSelfHeal(dao)

            val built = KnowledgeLocalSearchQueryBuilder.buildOrNull(query) ?: return emptyList()
            val q = built.raw
            val qNormalized = built.normalized
            val qNoSpace = built.noSpace
            val hasCjk = built.hasCjk
            try {
                // log removed: normalized query='$qNormalized' noSpace='$qNoSpace'
                val hex = qNormalized.toByteArray(Charsets.UTF_8).joinToString(" ") { String.format("%02x", it) }
                // log removed: normalized bytes info
            } catch (_: Throwable) {}

            LocalSearchDiagnostics.shouldEmitSnippetDiagnostics(qNormalized, hasCjk)

            // delegate heavy logic to extracted processor for clarity
            fun postProcess(entities: List<KnowledgeEntity>): List<KnowledgeItem> {
                return KnowledgeLocalSearchProcessor.postProcess(entities, built, entityToItem)
            }

            // one-time FTS count
            LocalSearchDiagnostics.logInitialFtsCount(dao)

            // delegate to search strategy classes to keep this method lean
            val result: List<KnowledgeEntity> = if (hasCjk) {
                // try CJK-specific first, then fall back to generic merge logic
                val cjkResults = CjkSearchStrategy.search(dao, built)
                if (cjkResults.isNotEmpty()) cjkResults else GenericSearchStrategy.search(dao, built)
            } else {
                GenericSearchStrategy.search(dao, built)
            }

            if (result.isNotEmpty()) {
                return postProcess(result)
            }

            emptyList()
        } catch (t: Throwable) {
            // On any unexpected failure, fall back to keyword search
            // log removed: searchLocal failed, falling back to keyword search
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
