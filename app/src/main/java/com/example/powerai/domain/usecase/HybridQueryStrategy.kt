package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.QueryResult
import com.example.powerai.domain.usecase.LocalSearchUseCase
import com.example.powerai.domain.usecase.AskAiUseCase

class HybridQueryStrategy(
    private val localSearchUseCase: LocalSearchUseCase,
    private val askAiUseCase: AskAiUseCase,
    private val isNetworkAvailable: () -> Boolean
) {
    suspend fun query(question: String): QueryResult {
        val localResults = localSearchUseCase.invoke(question)
        return if (localResults.size >= 3 || !isNetworkAvailable()) {
            QueryResult(
                answer = localResults.joinToString("\n") { it.content },
                references = localResults,
                confidence = 1.0f
            )
        } else {
            val referenceText = localResults.joinToString("\n") { it.content }
            val aiAnswer = askAiUseCase.invoke(question, referenceText)
            QueryResult(
                answer = aiAnswer,
                references = localResults,
                confidence = 0.5f + 0.1f * localResults.size
            )
        }
    }
}
