package com.example.powerai.ui.screen.hybrid

// TODO: 大部分业务逻辑已迁移至 UseCase（HybridQueryUseCase、HybridHistoryUseCase 等）
// 并且局部计算（例如 Gemma 推理）已委派[HybridUtils]
// 当前 ViewModel 仅负责 intent 路由、reducer、effect 通道与顶层执行 job 协调，
// 模式执行与反馈分别委派 [HybridModeExecutor] 与 [LocalAnswerFeedbackCoordinator]

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.settings.LocalAnswerFeedbackStore
import com.example.powerai.domain.model.LocalAnswerFeedback
import com.example.powerai.domain.usecase.HybridQueryUseCase
import com.example.powerai.ui.mvi.BaseMviViewModel
import com.example.powerai.ui.screen.main.DisplayMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class HybridViewModel
    @Inject
    constructor(
        private val useCase: HybridQueryUseCase,
        private val historyUseCase: com.example.powerai.domain.usecase.HybridHistoryUseCase,
        private val localAnswerFeedbackStore: LocalAnswerFeedbackStore,
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
        private val importer: com.example.powerai.data.importer.DocumentImportManager,
        private val savedStateHandle: SavedStateHandle,
        @javax.inject.Named("io")
        private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    ) : BaseMviViewModel<HybridIntent, HybridUiState, HybridEffect>(
            initialState = HybridUiState(),
        ),
        HybridStateAccess {
        private val uiControlState = HybridUiControlStateStore(savedStateHandle)
        private val runtimeSupport =
            HybridViewModelRuntimeSupport(
                importer = importer,
                context = context,
            )
        private val sideEffectCoordinator =
            HybridViewModelSideEffectCoordinator(
                historyUseCase = historyUseCase,
                importer = importer,
                ioDispatcher = ioDispatcher,
            )
        private val feedbackCoordinator =
            LocalAnswerFeedbackCoordinator(
                store = localAnswerFeedbackStore,
                stateAccess = this,
            )
        private val modeExecutor =
            HybridModeExecutor(
                useCase = useCase,
                importer = importer,
                ioDispatcher = ioDispatcher,
                stateAccess = this,
                sideEffectCoordinator = sideEffectCoordinator,
                feedback = feedbackCoordinator,
            )

        /** Current submission job; a new submit cancels the in-flight one. */
        private var submitJob: Job? = null

        override val state: HybridUiState
            get() = currentState

        override fun reduce(reducer: HybridUiState.() -> HybridUiState) {
            updateState(reducer)
        }

        init {
            viewModelScope.launch {
                // Collect auxiliary flows into the main state
                launch { uiControlState.localCurrentPage.collect { p -> updateState { copy(localCurrentPage = p) } } }
                launch { uiControlState.localCollapsedGroupKeys.collect { k -> updateState { copy(localCollapsedGroupKeys = k) } } }
                launch { uiControlState.localScrollIndex.collect { i -> updateState { copy(localScrollIndex = i) } } }
                launch { uiControlState.localScrollOffset.collect { o -> updateState { copy(localScrollOffset = o) } } }
                launch { uiControlState.smartScrollIndex.collect { i -> updateState { copy(smartScrollIndex = i) } } }
                launch { uiControlState.smartScrollOffset.collect { o -> updateState { copy(smartScrollOffset = o) } } }
                launch { sideEffectCoordinator.localSearchHistory.collect { h -> updateState { copy(localSearchHistory = h) } } }
                launch { sideEffectCoordinator.smartSearchHistory.collect { h -> updateState { copy(smartSearchHistory = h) } } }
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

        fun clearSmartSearchHistory() {
            sideEffectCoordinator.clearSmartHistory(viewModelScope)
        }

        fun recordSmartSearchQuery(query: String) {
            val normalized = query.trim()
            if (normalized.isBlank()) return
            sideEffectCoordinator.addSmartQuery(viewModelScope, normalized)
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
                onFollowUpQuery = ::submitQuery,
            )
        }

        fun submitQuery(question: String) {
            submitQuery(question, DisplayMode.SMART)
        }

        fun setWebSearchEnabled(enabled: Boolean) {
            updateState { copy(webSearchEnabled = enabled) }
        }

        fun submitQuery(
            question: String,
            mode: DisplayMode,
        ) {
            val preparation =
                HybridSubmissionStateFactory.prepare(
                    current = currentState,
                    question = question,
                    mode = mode,
                    askedAtMillis = System.currentTimeMillis(),
                )
            applySubmissionPreparation(preparation)

            submitJob =
                modeExecutor.submit(
                    scope = viewModelScope,
                    previousJob = submitJob,
                    preparation = preparation,
                    question = question,
                    mode = mode,
                    onUnhandledFailure = runtimeSupport::reportUnhandledFailure,
                )
        }

        fun clearCurrentResults() {
            updateState {
                HybridUiStateFactory.cleared(this).copy(
                    aiAnswer = null,
                    evidenceList = emptyList(),
                    localSummaryState = LocalSummaryState(),
                    localAnswerFeedback = LocalAnswerFeedback.NONE,
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

        fun setLocalScrollPosition(
            index: Int,
            offset: Int,
        ) {
            uiControlState.setLocalScrollPosition(index, offset)
        }

        fun setSmartScrollPosition(
            index: Int,
            offset: Int,
        ) {
            uiControlState.setSmartScrollPosition(index, offset)
        }

        fun toggleLocalAnswerThumbUp() {
            feedbackCoordinator.toggleUp()
        }

        fun toggleLocalAnswerThumbDown() {
            feedbackCoordinator.toggleDown()
        }

        private fun applySubmissionPreparation(preparation: HybridSubmissionPreparation) {
            updateState {
                preparation.nextUiState.copy(
                    aiAnswer = if (preparation.clearAiAnswer) null else aiAnswer,
                    evidenceList = if (preparation.clearEvidence) emptyList() else evidenceList,
                    localSummaryState = preparation.localSummaryState ?: localSummaryState,
                )
            }

            if (preparation.resetLocalUi) {
                resetLocalUiState()
            }
            if (preparation.resetSmartUi) {
                resetSmartUiState()
            }
            preparation.localSummaryState?.let { summaryState ->
                feedbackCoordinator.sync(summaryState.query)
            }
        }

        private fun resetLocalUiState() {
            uiControlState.resetLocal()
        }

        private fun resetSmartUiState() {
            uiControlState.resetSmart()
        }
    }
