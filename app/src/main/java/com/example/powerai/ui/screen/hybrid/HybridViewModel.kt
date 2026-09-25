package com.example.powerai.ui.screen.hybrid

// TODO: 大部分业务逻辑已迁移至 UseCase（HybridQueryUseCase、HybridHistoryUseCase 等）
// 并且局部计算（例如 Gemma 推理）已委派[HybridUtils]
// 当前 ViewModel 仅负责组合状态流与调度协程，
// 仍有少量 UI 状态更映射逻辑可在未来进一步下沉

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.settings.LocalAnswerFeedbackStore
import com.example.powerai.domain.model.LocalAnswerFeedback
import com.example.powerai.domain.model.LocalSearchEntry
import com.example.powerai.domain.usecase.HybridQueryUseCase
import com.example.powerai.ui.mvi.BaseMviViewModel
import com.example.powerai.ui.screen.main.DisplayMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HybridViewModel @Inject constructor(
    private val useCase: HybridQueryUseCase,
    private val historyUseCase: com.example.powerai.domain.usecase.HybridHistoryUseCase,
    private val localAnswerFeedbackStore: LocalAnswerFeedbackStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val importer: com.example.powerai.data.importer.DocumentImportManager,
    private val savedStateHandle: SavedStateHandle,
    @javax.inject.Named("io")
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : BaseMviViewModel<HybridIntent, HybridUiState, HybridEffect>(
    initialState = HybridUiState()
) {
    private val tag = "HybridVM"

    private val uiControlState = HybridUiControlStateStore(savedStateHandle)
    private val runtimeSupport = HybridViewModelRuntimeSupport(
        importer = importer,
        context = context
    )
    private val sideEffectCoordinator = HybridViewModelSideEffectCoordinator(
        historyUseCase = historyUseCase,
        importer = importer,
        ioDispatcher = ioDispatcher
    )

    init {
        viewModelScope.launch {
            // Collect auxiliary flows into the main state
            launch {
                uiControlState.localCurrentPage.collect { p -> updateState { copy(localCurrentPage = p) } }
            }
            launch {
                uiControlState.localCollapsedGroupKeys.collect { k -> updateState { copy(localCollapsedGroupKeys = k) } }
            }
            launch {
                uiControlState.localScrollIndex.collect { i -> updateState { copy(localScrollIndex = i) } }
            }
            launch {
                uiControlState.localScrollOffset.collect { o -> updateState { copy(localScrollOffset = o) } }
            }
            launch {
                uiControlState.smartScrollIndex.collect { i -> updateState { copy(smartScrollIndex = i) } }
            }
            launch {
                uiControlState.smartScrollOffset.collect { o -> updateState { copy(smartScrollOffset = o) } }
            }
            launch {
                sideEffectCoordinator.localSearchHistory.collect { h -> updateState { copy(localSearchHistory = h) } }
            }
            launch {
                sideEffectCoordinator.smartSearchHistory.collect { h -> updateState { copy(smartSearchHistory = h) } }
            }
            launch {
                sideEffectCoordinator.observeImportProgress(this) { progress ->
                    updateState { copy(importProgress = progress) }
                }
            }
        }
        sideEffectCoordinator.initialize(viewModelScope)
    }

    override fun onIntent(intent: HybridIntent) {
        when (intent) {
            is HybridIntent.SubmitQuery -> submitQuery(intent.question)
            is HybridIntent.SubmitQueryWithMode -> submitQuery(intent.question, intent.mode)
            is HybridIntent.ClearResults -> clearCurrentResults()
            is HybridIntent.SetWebSearchEnabled -> setWebSearchEnabled(intent.enabled)
            is HybridIntent.ImportDocument -> importDocument(intent.uri)
            is HybridIntent.SetLocalCurrentPage -> setLocalCurrentPage(intent.page)
            is HybridIntent.ToggleLocalCollapsedGroup -> toggleLocalCollapsedGroupKey(intent.key)
            is HybridIntent.SetLocalCollapsedGroupKeys -> setLocalCollapsedGroupKeys(intent.keys)
            is HybridIntent.SetLocalScrollPosition -> setLocalScrollPosition(intent.index, intent.offset)
            is HybridIntent.SetSmartScrollPosition -> setSmartScrollPosition(intent.index, intent.offset)
            is HybridIntent.ToggleLocalAnswerThumbUp -> toggleLocalAnswerThumbUp()
            is HybridIntent.ToggleLocalAnswerThumbDown -> toggleLocalAnswerThumbDown()
            is HybridIntent.RecordSmartSearchQuery -> recordSmartSearchQuery(intent.query)
            is HybridIntent.ClearSmartSearchHistory -> clearSmartSearchHistory()
            is HybridIntent.ClearLocalSearchHistory -> clearLocalSearchHistory()
        }
    }

    // last launched gemma inference job (used by tests to await completion)
    var lastGemmaJob: kotlinx.coroutines.Job? = null

    private fun addLocalSearchQuery(query: String) {
        sideEffectCoordinator.addLocalQuery(viewModelScope, query)
    }

    private fun addSmartSearchQuery(query: String) {
        sideEffectCoordinator.addSmartQuery(viewModelScope, query)
    }

    fun clearSmartSearchHistory() {
        sideEffectCoordinator.clearSmartHistory(viewModelScope)
    }

    fun recordSmartSearchQuery(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        addSmartSearchQuery(normalized)
    }

    fun clearLocalSearchHistory() {
        sideEffectCoordinator.clearLocalHistory(viewModelScope)
    }

    /**
     * Import a document referenced by `uri` using the injected
     * `DocumentImportManager`. The manager owns the application `Context`
     * and repository wiring so ViewModels remain testable and DI-friendly.
     */
    fun importDocument(uri: android.net.Uri) {
        runtimeSupport.importDocument(
            uri = uri,
            currentQuestion = currentState.question,
            onFollowUpQuery = ::submitQuery
        )
    }

    fun submitQuery(question: String) {
        submitQuery(question, DisplayMode.SMART)
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        updateState { copy(webSearchEnabled = enabled) }
    }

    fun submitQuery(question: String, mode: DisplayMode) {
        val preparation = HybridSubmissionStateFactory.prepare(
            current = currentState,
            question = question,
            mode = mode,
            askedAtMillis = System.currentTimeMillis()
        )
        applySubmissionPreparation(preparation)

        viewModelScope.launch {
            try {
                when (mode) {
                    DisplayMode.LOCAL -> handleLocalMode(preparation.sanitizedQuestion)
                    DisplayMode.AI -> handleAiMode(question)
                    DisplayMode.SMART -> handleSmartMode(preparation.ftsQuery, preparation.sanitizedQuestion)
                }
            } catch (t: Throwable) {
                runtimeSupport.reportUnhandledFailure(t)
            }
        }
    }

    fun clearCurrentResults() {
        updateState {
            HybridUiStateFactory.cleared(this).copy(
                aiAnswer = null,
                evidenceList = emptyList(),
                localSummaryState = LocalSummaryState(),
                localAnswerFeedback = LocalAnswerFeedback.NONE
            )
        }
        resetLocalUiState()
        resetSmartUiState()
    }

    fun setLocalCurrentPage(page: Int) {
        uiControlState.setLocalCurrentPage(page)
    }

    fun setLocalCollapsedGroupKeys(keys: Set<String>) {
        uiControlState.setLocalCollapsedGroupKeys(keys)
    }

    fun toggleLocalCollapsedGroupKey(key: String) {
        uiControlState.toggleLocalCollapsedGroupKey(key)
    }

    fun setLocalScrollPosition(index: Int, offset: Int) {
        uiControlState.setLocalScrollPosition(index, offset)
    }

    fun setSmartScrollPosition(index: Int, offset: Int) {
        uiControlState.setSmartScrollPosition(index, offset)
    }

    private fun resetLocalUiState() {
        uiControlState.resetLocal()
    }

    private fun resetSmartUiState() {
        uiControlState.resetSmart()
    }

    private fun applySubmissionPreparation(preparation: HybridSubmissionPreparation) {
        updateState { preparation.nextUiState.copy(
            aiAnswer = if (preparation.clearAiAnswer) null else aiAnswer,
            evidenceList = if (preparation.clearEvidence) emptyList() else evidenceList,
            localSummaryState = preparation.localSummaryState ?: localSummaryState
        ) }

        if (preparation.resetLocalUi) {
            resetLocalUiState()
        }
        if (preparation.resetSmartUi) {
            resetSmartUiState()
        }
        preparation.localSummaryState?.let { summaryState ->
            syncLocalAnswerFeedback(summaryState.query)
        }
    }

    fun toggleLocalAnswerThumbUp() {
        val next = if (currentState.localAnswerFeedback == LocalAnswerFeedback.UP) {
            LocalAnswerFeedback.NONE
        } else {
            LocalAnswerFeedback.UP
        }
        setLocalAnswerFeedback(next)
    }

    fun toggleLocalAnswerThumbDown() {
        val next = if (currentState.localAnswerFeedback == LocalAnswerFeedback.DOWN) {
            LocalAnswerFeedback.NONE
        } else {
            LocalAnswerFeedback.DOWN
        }
        setLocalAnswerFeedback(next)
    }

    private fun setLocalAnswerFeedback(feedback: LocalAnswerFeedback) {
        val query = currentState.localSummaryState.query
        if (query.isBlank()) return
        localAnswerFeedbackStore.setFeedback(query, feedback)
        updateState { copy(localAnswerFeedback = feedback) }
    }

    private fun syncLocalAnswerFeedback(query: String) {
        val feedback = localAnswerFeedbackStore.getFeedback(query)
        updateState { copy(localAnswerFeedback = feedback) }
    }

    private fun onLocalGemmaResult(query: String, text: String) {
        val currentSummary = currentState.localSummaryState
        if (currentSummary.query != query) return

        val trimmed = text.trim()
        updateState {
            copy(
                aiAnswer = trimmed.ifBlank { null },
                localSummaryState = LocalSummaryStateFactory.applyGemmaResult(currentSummary, trimmed)
            )
        }
    }

    private fun applyLocalModePayload(payload: LocalModeUiPayload) {
        updateState {
            HybridUiStateFactory.localCompleted(
                current = this,
                references = payload.references
            ).copy(
                evidenceList = payload.evidence,
                localSummaryState = mergeLocalSummaryState(current = localSummaryState, incoming = payload.summaryState)
            )
        }
        syncLocalAnswerFeedback(payload.summaryState.query)
        lastGemmaJob = payload.gemmaJob
        observeLocalGemmaJob(payload.summaryState.query, payload.gemmaJob)
    }

    private fun mergeLocalSummaryState(
        current: LocalSummaryState,
        incoming: LocalSummaryState
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
            errorMessage = incoming.errorMessage
        )
    }

    private fun observeLocalGemmaJob(query: String, job: kotlinx.coroutines.Job?) {
        if (job == null) return
        viewModelScope.launch {
            try {
                job.join()
            } catch (_: Throwable) {
            }

            if (lastGemmaJob == job) {
                lastGemmaJob = null
            }

            updateState {
                if (localSummaryState.query != query || !localSummaryState.isStreaming) {
                    this
                } else {
                    copy(localSummaryState = LocalSummaryStateFactory.finishGemmaWithoutResult(localSummaryState))
                }
            }
        }
    }

    private fun applyAiModeAnswer(answer: String) {
        updateState { HybridUiStateFactory.aiCompleted(this, answer) }
    }

    private fun applySmartModePayload(payload: SmartModeUiPayload) {
        updateState {
            HybridUiStateFactory.smartCompleted(
                current = this,
                answer = payload.answer,
                references = payload.references
            ).copy(
                evidenceList = payload.evidence
            )
        }
    }

    private suspend fun handleLocalMode(sanitized: String) {
        val outcome = try {
            withContext(ioDispatcher) {
                importer.importAssetsIfNeed()
                useCase.localMode(
                    question = sanitized,
                    rawQuestion = sanitized,
                    scope = viewModelScope,
                    onGemmaResult = { text -> onLocalGemmaResult(query = sanitized, text = text) }
                )
            }
        } catch (_: Throwable) {
            HybridModeFallbackFactory.localFailure(sanitized)
        }

        val payload = LocalModeUiPayloadFactory.fromOutcome(
            query = sanitized,
            outcome = outcome
        )
        applyLocalModePayload(payload)
        addLocalSearchQuery(sanitized)
    }

    private suspend fun handleAiMode(question: String) {
        val answer = try {
            withContext(ioDispatcher) {
                useCase.aiMode(question, webSearchEnabled = currentState.webSearchEnabled)
            }
        } catch (t: Throwable) {
            HybridModeFallbackFactory.aiFailure(t)
        }
        applyAiModeAnswer(answer)
    }

    private suspend fun handleSmartMode(ftsQuery: String, sanitized: String) {
        val result = try {
            withContext(ioDispatcher) {
                importer.importAssetsIfNeed()
                useCase.invoke(ftsQuery)
            }
        } catch (t: Throwable) {
            HybridModeFallbackFactory.smartFailure(t)
        }
        val payload = SmartModeUiPayloadFactory.fromResult(result)
        applySmartModePayload(payload)
        // record smart query
        addSmartSearchQuery(sanitized)
    }
}
