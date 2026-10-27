package com.example.powerai.domain.usecase

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.domain.generator.PromptBuilder
import com.example.powerai.domain.model.KnowledgeEntry
import com.example.powerai.domain.model.QueryResult
import com.example.powerai.domain.retrieval.RetrievalFusionUseCase
import com.example.powerai.engine.ai.GemmaInferenceLauncher
import com.example.powerai.engine.ai.GemmaLocalInference
import javax.inject.Inject

/**
 * App-facing hybrid query facade: delegates local evidence to
 * [LocalHybridQueryOrchestrator] (:core:domain) and owns Gemma / AskAi side effects (Phase B4).
 */
class HybridQueryUseCase
    @Inject
    constructor(
        private val localSearchUseCase: RetrievalFusionUseCase,
        private val askAiUseCase: AskAiUseCase,
        private val gemmaLocalInference: GemmaLocalInference,
        private val localHybridOrchestrator: LocalHybridQueryOrchestrator,
    ) {
        /**
         * Result returned by [localMode]: a pair of the raw retrievals (for evidence
         * listing) plus the corresponding knowledge items used for UI references.
         */
        data class LocalQueryResult(
            val retrievals: List<com.example.powerai.core.model.RetrievalResult>,
            val items: List<KnowledgeItem>,
            val totalCandidateCount: Int,
            val refinedCandidateCount: Int,
            val discardedCandidates: List<LocalDiscardedCandidate> = emptyList(),
            val extractiveAnswer: LocalExtractiveAnswer? = null,
        )

        data class LocalModeResult(
            val query: LocalQueryResult,
            val assessment: LocalEvidenceAssessment,
            val gemmaJob: kotlinx.coroutines.Job?,
        )

        /**
         * Performs the logic formerly present in `HybridViewModel.submitQuery`'s
         * LOCAL branch.  Returns both the retrieval objects (for gallery) and the
         * mapped knowledge items (for answer/reference display).
         */
        suspend fun localMode(
            question: String,
            rawQuestion: String,
            scope: kotlinx.coroutines.CoroutineScope,
            onGemmaResult: (String) -> Unit,
        ): LocalModeResult {
            val evidence = localHybridOrchestrator.run(question, rawQuestion)
            val gemmaJob =
                if (evidence.retrievals.isNotEmpty() &&
                    evidence.assessment.decision == LocalAnswerDecision.USE_GEMMA_ANSWER
                ) {
                    launchGemma(rawQuestion, evidence, scope, onGemmaResult)
                } else {
                    null
                }

            return LocalModeResult(
                query =
                    LocalQueryResult(
                        retrievals = evidence.retrievals,
                        items = evidence.items,
                        totalCandidateCount = evidence.totalCandidateCount,
                        refinedCandidateCount = evidence.refinedCandidateCount,
                        discardedCandidates = evidence.discardedCandidates,
                        extractiveAnswer = evidence.extractiveAnswer,
                    ),
                assessment = evidence.assessment,
                gemmaJob = gemmaJob,
            )
        }

        private fun launchGemma(
            rawQuestion: String,
            evidence: LocalHybridQueryOrchestrator.Result,
            scope: kotlinx.coroutines.CoroutineScope,
            onGemmaResult: (String) -> Unit,
        ): kotlinx.coroutines.Job {
            val assessment = evidence.assessment
            val promptMode =
                when (assessment.intent) {
                    LocalAnswerIntent.FACT_QUESTION -> PromptBuilder.LocalPromptMode.FACT_ANSWER
                    LocalAnswerIntent.TOPIC_OVERVIEW -> PromptBuilder.LocalPromptMode.TOPIC_OVERVIEW
                    LocalAnswerIntent.PROCEDURE -> PromptBuilder.LocalPromptMode.PROCEDURE_GUIDE
                    LocalAnswerIntent.COMPARISON -> PromptBuilder.LocalPromptMode.COMPARISON_SUMMARY
                }
            val prompt =
                PromptBuilder(tokenBudget = 1400)
                    .buildLocalGroundedPrompt(
                        question = rawQuestion,
                        results = LocalAnswerPlanner.selectPromptEvidence(evidence.retrievals, assessment),
                        mode = promptMode,
                    )
            return GemmaInferenceLauncher.launchGemmaInference(
                scope = scope,
                prompt = prompt,
                gemma = gemmaLocalInference,
                maxTokens =
                    when (assessment.intent) {
                        LocalAnswerIntent.FACT_QUESTION -> 180
                        LocalAnswerIntent.TOPIC_OVERVIEW -> 220
                        LocalAnswerIntent.PROCEDURE -> 240
                        LocalAnswerIntent.COMPARISON -> 240
                    },
                onResult = onGemmaResult,
            )
        }

        /**
         * Performs the AI‑only query formerly in ViewModel.  Any exceptions are
         * caught and converted to a simple error string.
         */
        suspend fun aiMode(
            question: String,
            webSearchEnabled: Boolean,
        ): String {
            return try {
                askAiUseCase.invokeAiSearch(question, webSearchEnabled = webSearchEnabled)
            } catch (t: Throwable) {
                "AI error: ${t.message ?: t::class.simpleName}"
            }
        }

        /**
         * 混合查询：本地优先，少则调AI，返回QueryResult
         */
        suspend fun invoke(question: String): QueryResult {
            // Force ANN-backed retrieval for hybrid (SMART) queries so the native engine is prioritized.
            val localResults: List<KnowledgeItem> = localSearchUseCase.invoke(question, limit = 10, forceAnn = true)
            val numberedEvidence = buildNumberedEvidence(localResults)
            val answer =
                try {
                    askAiUseCase.invoke(question, numberedEvidence)
                } catch (t: Throwable) {
                    "AI error: ${t.message ?: t::class.simpleName}"
                }
            val confidence: Float =
                when {
                    localResults.isEmpty() -> 0.3f
                    localResults.size >= 5 -> 0.9f
                    else -> 0.5f + 0.08f * localResults.size
                }.coerceIn(0f, 1f)
            return QueryResult(
                answer = answer,
                references = localResults,
                confidence = confidence,
            )
        }

        private fun buildNumberedEvidence(items: List<KnowledgeItem>): String {
            if (items.isEmpty()) return "(无证据)"
            return items.take(10).mapIndexed { index, item ->
                val number = index + 1
                val meta =
                    buildString {
                        if (item.source.isNotBlank()) append(item.source)
                        item.pageNumber?.let { append(" · p$it") }
                        item.hitBlockIndex?.let { append(" · 命中块$it") }
                    }.ifBlank { "未提供来" }

                val excerpt =
                    item.content
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
            val mapped =
                localResults.map {
                    KnowledgeEntry(
                        id = it.id.toString(),
                        title = it.title,
                        content = it.content,
                        category = it.category,
                        source = it.source,
                        status = "local",
                    )
                }.toMutableList()

            if (localResults.size < 3) {
                val referenceText = buildNumberedEvidence(localResults)
                val aiAnswer =
                    try {
                        askAiUseCase.invoke(keyword, referenceText)
                    } catch (t: Throwable) {
                        "AI error: ${t.message ?: t::class.simpleName}"
                    }
                val aiEntry =
                    KnowledgeEntry(
                        id = "ai-${System.currentTimeMillis()}",
                        title = "AI Answer",
                        content = aiAnswer,
                        category = "ai",
                        source = "DeepSeePK",
                        status = "ai",
                    )
                mapped.add(aiEntry)
            }
            return mapped.toList()
        }
    }
