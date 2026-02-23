package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.KnowledgeEntry
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.model.QueryResult
import javax.inject.Inject

class HybridQueryUseCase @Inject constructor(
    private val localSearchUseCase: com.example.powerai.domain.usecase.RetrievalFusionUseCase,
    private val askAiUseCase: AskAiUseCase
) {
    /**
     * 混合查询：本地优先，少则调AI，返回QueryResult
     */
    suspend fun invoke(question: String): QueryResult {
        // Force ANN-backed retrieval for hybrid (SMART) queries so the native engine is prioritized.
        val localResults: List<KnowledgeItem> = localSearchUseCase.invoke(question, limit = 10, forceAnn = true)
        val numberedEvidence = buildNumberedEvidence(localResults)
        val answer = try {
            askAiUseCase.invoke(question, numberedEvidence)
        } catch (t: Throwable) {
            "AI error: ${t.message ?: t::class.simpleName}"
        }
        val confidence: Float = when {
            localResults.isEmpty() -> 0.3f
            localResults.size >= 5 -> 0.9f
            else -> 0.5f + 0.08f * localResults.size
        }.coerceIn(0f, 1f)
        return QueryResult(
            answer = answer,
            references = localResults,
            confidence = confidence
        )
    }

    private fun buildNumberedEvidence(items: List<KnowledgeItem>): String {
        if (items.isEmpty()) return "(无证据)"
        return items.take(10).mapIndexed { index, item ->
            val number = index + 1
            val meta = buildString {
                if (item.source.isNotBlank()) append(item.source)
                item.pageNumber?.let { append(" · 第${it}页") }
                item.hitBlockIndex?.let { append(" · 命中块$it") }
            }.ifBlank { "未提供来源" }

            val excerpt = item.content
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()
                .take(380)

            """
                [$number] ${item.title}
                来源：$meta
                摘录：$excerpt
            """.trimIndent()
        }.joinToString("\n\n")
    }

    /**
     * Hybrid query that returns a list of data-layer KnowledgeEntry objects
     * combining local JSON search results and an optional AI-generated answer
     * as an appended entry when local results are sparse.
     */
    suspend fun hybridQuery(keyword: String): List<KnowledgeEntry> {
        val localResults = localSearchUseCase.invoke(keyword)
        // map domain KnowledgeItem -> domain KnowledgeEntry
        val mapped = localResults.map {
            KnowledgeEntry(
                id = it.id.toString(),
                title = it.title,
                content = it.content,
                category = it.category,
                source = it.source,
                status = "local"
            )
        }.toMutableList()

        if (localResults.size < 3) {
            val referenceText = buildNumberedEvidence(localResults)
            val aiAnswer = try {
                askAiUseCase.invoke(keyword, referenceText)
            } catch (t: Throwable) {
                "AI error: ${t.message ?: t::class.simpleName}"
            }
            val aiEntry = KnowledgeEntry(
                id = "ai-${System.currentTimeMillis()}",
                title = "AI Answer",
                content = aiAnswer,
                category = "ai",
                source = "DeepSeePK",
                status = "ai"
            )
            mapped.add(aiEntry)
        }
        return mapped.toList()
    }
}
