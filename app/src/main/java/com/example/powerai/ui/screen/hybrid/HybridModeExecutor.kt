package com.example.powerai.ui.screen.hybrid

import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.domain.usecase.HybridQueryUseCase
import com.example.powerai.ui.screen.main.DisplayMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read/reduce access to [HybridUiState] shared by the hybrid coordinators. */
internal interface HybridStateAccess {
    val state: HybridUiState

    fun reduce(reducer: HybridUiState.() -> HybridUiState)
}

/**
 * Executes Local/AI/Smart submissions for [HybridViewModel]: mode dispatch,
 * unified result/error mapping, gemma streaming observation and cancellation
 * propagation. A cancelled submit never publishes its payload; history
 * recording delegates to the existing [HybridViewModelSideEffectCoordinator].
 */
internal class HybridModeExecutor(
    private val useCase: HybridQueryUseCase,
    private val importer: DocumentImportManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val stateAccess: HybridStateAccess,
    private val sideEffectCoordinator: HybridViewModelSideEffectCoordinator,
    private val feedback: LocalAnswerFeedbackCoordinator,
) {
    private var lastGemmaJob: Job? = null

    /** Runs one submission; the caller owns (and cancels) the returned job. */
    @Suppress("TooGenericExceptionCaught")
    fun submit(
        scope: CoroutineScope,
        preparation: HybridSubmissionPreparation,
        question: String,
        mode: DisplayMode,
        onUnhandledFailure: (Throwable) -> Unit,
    ): Job =
        scope.launch {
            try {
                when (mode) {
                    DisplayMode.LOCAL -> handleLocalMode(scope, preparation.sanitizedQuestion)
                    DisplayMode.AI -> handleAiMode(question)
                    DisplayMode.SMART -> handleSmartMode(scope, preparation.ftsQuery, preparation.sanitizedQuestion)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                onUnhandledFailure(t)
            }
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun handleLocalMode(
        scope: CoroutineScope,
        sanitized: String,
    ) {
        val outcome =
            try {
                withContext(ioDispatcher) {
                    importer.importAssetsIfNeed()
                    useCase.localMode(
                        question = sanitized,
                        rawQuestion = sanitized,
                        scope = scope,
                        onGemmaResult = { text -> onLocalGemmaResult(query = sanitized, text = text) },
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                HybridModeFallbackFactory.localFailure(sanitized)
            }

        val payload =
            LocalModeUiPayloadFactory.fromOutcome(
                query = sanitized,
                outcome = outcome,
            )
        applyLocalModePayload(scope, payload)
        sideEffectCoordinator.addLocalQuery(scope, sanitized)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun handleAiMode(question: String) {
        val answer =
            try {
                withContext(ioDispatcher) {
                    useCase.aiMode(question, webSearchEnabled = stateAccess.state.webSearchEnabled)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                HybridModeFallbackFactory.aiFailure(t)
            }
        applyAiModeAnswer(answer)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun handleSmartMode(
        scope: CoroutineScope,
        ftsQuery: String,
        sanitized: String,
    ) {
        val result =
            try {
                withContext(ioDispatcher) {
                    importer.importAssetsIfNeed()
                    useCase.invoke(ftsQuery)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                HybridModeFallbackFactory.smartFailure(t)
            }
        val payload = SmartModeUiPayloadFactory.fromResult(result)
        applySmartModePayload(payload)
        sideEffectCoordinator.addSmartQuery(scope, sanitized)
    }

    private fun onLocalGemmaResult(
        query: String,
        text: String,
    ) {
        val currentSummary = stateAccess.state.localSummaryState
        if (currentSummary.query != query) return

        val trimmed = text.trim()
        stateAccess.reduce {
            copy(
                aiAnswer = trimmed.ifBlank { null },
                localSummaryState = LocalSummaryStateFactory.applyGemmaResult(currentSummary, trimmed),
            )
        }
    }

    private fun applyLocalModePayload(
        scope: CoroutineScope,
        payload: LocalModeUiPayload,
    ) {
        stateAccess.reduce {
            HybridUiStateFactory.localCompleted(
                current = this,
                references = payload.references,
            ).copy(
                evidenceList = payload.evidence,
                localSummaryState = mergeLocalSummaryState(current = localSummaryState, incoming = payload.summaryState),
            )
        }
        feedback.sync(payload.summaryState.query)
        lastGemmaJob = payload.gemmaJob
        observeLocalGemmaJob(scope, payload.summaryState.query, payload.gemmaJob)
    }

    private fun mergeLocalSummaryState(
        current: LocalSummaryState,
        incoming: LocalSummaryState,
    ): LocalSummaryState {
        val hasCompletedGemmaAnswer =
            current.query == incoming.query &&
                incoming.isStreaming &&
                current.summary.isNotBlank() &&
                !current.isStreaming &&
                (current.pageState == LocalPageState.ANSWER_READY || current.pageState == LocalPageState.TOPIC_OVERVIEW)

        if (!hasCompletedGemmaAnswer) return incoming

        return current.copy(
            diagnosticSnapshot = incoming.diagnosticSnapshot,
            diagnostics = incoming.diagnostics,
            evidenceCount = incoming.evidenceCount,
            totalEvidenceCount = incoming.totalEvidenceCount,
            canRunGemma = incoming.canRunGemma,
            errorMessage = incoming.errorMessage,
        )
    }

    private fun observeLocalGemmaJob(
        scope: CoroutineScope,
        query: String,
        job: Job?,
    ) {
        if (job == null) return
        scope.launch {
            try {
                job.join()
            } catch (_: Throwable) {
            }

            if (lastGemmaJob == job) {
                lastGemmaJob = null
            }

            stateAccess.reduce {
                if (localSummaryState.query != query || !localSummaryState.isStreaming) {
                    this
                } else {
                    copy(localSummaryState = LocalSummaryStateFactory.finishGemmaWithoutResult(localSummaryState))
                }
            }
        }
    }

    private fun applyAiModeAnswer(answer: String) {
        stateAccess.reduce { HybridUiStateFactory.aiCompleted(this, answer) }
    }

    private fun applySmartModePayload(payload: SmartModeUiPayload) {
        stateAccess.reduce {
            HybridUiStateFactory.smartCompleted(
                current = this,
                answer = payload.answer,
                references = payload.references,
            ).copy(
                evidenceList = payload.evidence,
            )
        }
    }
}
