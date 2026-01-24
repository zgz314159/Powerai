package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.domain.model.QueryResult

import javax.inject.Inject

class HybridQueryUseCase @Inject constructor(
    private val localSearchUseCase: LocalSearchUseCase,
    private val askAiUseCase: AskAiUseCase
) {
    /**
     * 混合查询：本地优先，少则调AI，返回QueryResult
     */
    suspend fun invoke(question: String): QueryResult {
        val localResults: List<KnowledgeItem> = localSearchUseCase.invoke(question)
        val answer: String
        val confidence: Float
        if (localResults.size >= 3) {
            answer = localResults.joinToString("\n") { it.content }
            confidence = 1.0f
        } else {
            val referenceText = localResults.joinToString("\n") { it.content }
            answer = try {
                askAiUseCase.invoke(question, referenceText)
            } catch (t: Throwable) {
                "AI error: ${t.message ?: t::class.simpleName}"
            }
            confidence = 0.5f + 0.1f * localResults.size
        }
        return QueryResult(
            answer = answer,
            references = localResults,
            confidence = confidence
        )
    }

    /**
     * Hybrid query that returns a list of data-layer KnowledgeEntry objects
     * combining local JSON search results and an optional AI-generated answer
     * as an appended entry when local results are sparse.
     */
    suspend fun hybridQuery(keyword: String): List<com.example.powerai.data.knowledge.KnowledgeRepository.KnowledgeEntry> {
        val localResults = localSearchUseCase.invoke(keyword)
        // map domain KnowledgeItem -> data KnowledgeEntry
        val mapped = localResults.map {
            com.example.powerai.data.knowledge.KnowledgeRepository.KnowledgeEntry(
                id = it.id.toString(),
                title = it.title,
                content = it.content,
                category = it.category,
                source = it.source,
                status = "local"
            )
        }.toMutableList()

        if (localResults.size < 3) {
            val referenceText = localResults.joinToString("\n") { it.content }
            val aiAnswer = try {
                askAiUseCase.invoke(keyword, referenceText)
            } catch (t: Throwable) {
                "AI error: ${t.message ?: t::class.simpleName}"
            }
            val aiEntry = com.example.powerai.data.knowledge.KnowledgeRepository.KnowledgeEntry(
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
