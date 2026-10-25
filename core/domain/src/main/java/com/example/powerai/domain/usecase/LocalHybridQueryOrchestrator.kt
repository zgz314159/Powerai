package com.example.powerai.domain.usecase

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.domain.retrieval.RetrievalFusionUseCase
import javax.inject.Inject

/**
 * Pure local-mode retrieval + evidence orchestration (no Gemma / AskAi side effects).
 * Phase B4: lives in :core:domain so the query chain converges without engine coupling.
 */
class LocalHybridQueryOrchestrator @Inject constructor(
    private val localSearchUseCase: RetrievalFusionUseCase,
    private val knowledgeRepository: KnowledgeRepository,
) {
    data class Result(
        val retrievals: List<RetrievalResult>,
        val items: List<KnowledgeItem>,
        val totalCandidateCount: Int,
        val refinedCandidateCount: Int,
        val discardedCandidates: List<LocalDiscardedCandidate> = emptyList(),
        val extractiveAnswer: LocalExtractiveAnswer? = null,
        val assessment: LocalEvidenceAssessment,
    )

    suspend fun run(question: String, rawQuestion: String): Result {
        val raw = localSearchUseCase.invokeResults(question)
        val materialized = raw.map { rr ->
            val item = rr.item
                ?: rr.id?.takeIf { it > 0L }?.let { knowledgeRepository.getLocalItemById(it) }
                ?: KnowledgeItem(
                    id = -1L,
                    title = rr.metadata["title"] ?: "(无标题)",
                    content = rr.metadata["snippet"] ?: "",
                    source = rr.metadata["source"] ?: rr.source,
                    pageNumber = null,
                    category = "",
                    keywords = emptyList(),
                    hitBlockIndex = null,
                    hitBlockId = null,
                )
            rr.copy(item = item)
        }
        val refinement = LocalEvidenceRefiner.refine(rawQuestion, materialized)
        val preliminaryRetrievals = refinement.retrievals
        val preliminaryAssessment = LocalAnswerPlanner.assess(rawQuestion, preliminaryRetrievals)
        val (extractiveAnswer, refinedRetrievals) =
            resolveEvidenceTargets(preliminaryRetrievals, preliminaryAssessment, rawQuestion)
        val items = refinedRetrievals.mapNotNull { it.item }
        val assessment = LocalAnswerPlanner.assess(rawQuestion, refinedRetrievals)
        return Result(
            retrievals = refinedRetrievals,
            items = items,
            totalCandidateCount = refinement.totalCandidateCount,
            refinedCandidateCount = refinement.refinedCandidateCount,
            discardedCandidates = refinement.discardedCandidates,
            extractiveAnswer = if (refinedRetrievals.isEmpty()) null else extractiveAnswer,
            assessment = assessment,
        )
    }

    private suspend fun attachExtractiveHighlightTargets(
        retrievals: List<RetrievalResult>,
        extractiveAnswer: LocalExtractiveAnswer?,
    ): List<RetrievalResult> {
        if (extractiveAnswer == null) return retrievals
        val highlight = extractiveAnswer.text.trim()
        if (highlight.isBlank()) return retrievals

        return retrievals.map { retrieval ->
            val item = retrieval.item ?: return@map retrieval
            val matched = when {
                extractiveAnswer.retrievalId != null -> item.id == extractiveAnswer.retrievalId
                retrievals.size == 1 -> true
                else -> false
            }
            if (!matched) return@map retrieval

            val target = try {
                knowledgeRepository.resolveHighlightTarget(item.id, highlight)
            } catch (_: Throwable) {
                null
            }
            retrieval.copy(
                item = item.copy(
                    highlightHint = highlight,
                    hitBlockIndex = target?.blockIndex ?: item.hitBlockIndex,
                    hitBlockId = target?.blockId ?: item.hitBlockId,
                ),
            )
        }
    }

    private suspend fun resolveEvidenceTargets(
        retrievals: List<RetrievalResult>,
        assessment: LocalEvidenceAssessment,
        query: String,
    ): Pair<LocalExtractiveAnswer?, List<RetrievalResult>> {
        val answer = LocalExtractiveAnswerBuilder.extract(query, retrievals, assessment)
        val highlighted = attachExtractiveHighlightTargets(retrievals, answer)
        val redirected = redirectTableLabelHits(highlighted, query)
        return alignExtractiveAnswer(answer, highlighted, redirected) to redirected
    }

    private suspend fun redirectTableLabelHits(
        retrievals: List<RetrievalResult>,
        matchedText: String,
    ): List<RetrievalResult> {
        if (retrievals.isEmpty()) return retrievals
        return retrievals.map { retrieval ->
            val item = retrieval.item ?: return@map retrieval
            val redirected =
                try {
                    knowledgeRepository.resolveTableLabelTargets(listOf(item), matchedText).firstOrNull()
                } catch (_: Throwable) {
                    null
                }
            if (redirected != null && redirected != item) retrieval.copy(item = redirected) else retrieval
        }
    }

    private fun alignExtractiveAnswer(
        answer: LocalExtractiveAnswer?,
        before: List<RetrievalResult>,
        after: List<RetrievalResult>,
    ): LocalExtractiveAnswer? {
        val oldId = answer?.retrievalId
        val index = if (oldId == null) -1 else before.indexOfFirst { it.item?.id == oldId }
        val newId = if (index >= 0) after.getOrNull(index)?.item?.id ?: oldId else oldId
        return if (answer != null && newId != null && newId != oldId) answer.copy(retrievalId = newId) else answer
    }
}
