package com.example.powerai.ui.screen.main

import com.example.powerai.domain.model.chat.AiStreamState

import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.domain.usecase.AiStreamUseCase
import kotlinx.coroutines.*
import com.example.powerai.core.model.util.Cancellable

/**
 * Helper class that encapsulates the stateful logic for performing a streaming
 * request through [AiStreamUseCase].
 *
 * Keeping this logic outside the ViewModel simplifies unit tests and allows the
 * ViewModel to focus purely on UI state; the orchestrator is itself easily
 * mockable since it is declared `open` and the two public methods are open.
 */
open class AiStreamOrchestrator(
    private val useCase: AiStreamUseCase,
    private val scope: CoroutineScope,
    // dispatcher used for launching jobs; allows tests to override default IO
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    var currentJob: Job? = null
    var currentCancellable: Cancellable? = null

    open fun startStream(
        sessions: List<ChatSession>,
        currentSessionId: Long?,
        turnId: Long,
        userInput: String,
        webSearchEnabled: Boolean,
        onState: (AiStreamState) -> Unit,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit,
        // onWebSources is retained for backwards compatibility but no longer
        // required: callers may ignore it since webSources are surfaced via
        // [AiStreamUseCase.webSources] flow. A default no-op is provided.
        onWebSources: (List<String>) -> Unit = {},
        updateCurrentTurnSources: (Long, List<String>) -> Unit,
        getHistoryTurns: () -> List<ChatTurn>,
        updateTurnRetryAllowed: (Long, Boolean) -> Unit,
        updateSessions: (List<ChatSession>) -> Unit
    ): Job {
        currentJob?.cancel()
        val done = CompletableDeferred<Unit>()

        currentJob = scope.launch(dispatcher + CoroutineExceptionHandler { _, t ->
            onLoading(false)
            onError(t.message ?: "Unknown error")
            try { currentCancellable?.cancel() } catch (_: Throwable) {}
            done.complete(Unit)
        }) {
            onLoading(true)
            try { currentCancellable?.cancel() } catch (_: Throwable) {}
            currentCancellable = useCase.performStreamRequest(
                scope = scope,
                sessions = sessions,
                currentSessionId = currentSessionId,
                turnId = turnId,
                userInput = userInput,
                webSearchEnabled = webSearchEnabled,
                onState = { st -> onState(st); if (st is AiStreamState.Success && !isActive) done.complete(Unit) },
                onLoading = onLoading,
                onError = { msg -> onError(msg); done.complete(Unit) },
                onWebSources = onWebSources,
                updateCurrentTurnSources = updateCurrentTurnSources,
                getHistoryTurns = getHistoryTurns,
                updateTurnRetryAllowed = updateTurnRetryAllowed,
                updateSessions = updateSessions
            )
            try { done.await() } catch (_: Exception) {}
            finally {
                try { currentCancellable?.cancel() } catch (_: Throwable) {}
            }
        }
        return currentJob!!
    }

    open fun stopStream() {
        currentJob?.cancel()
        currentJob = null
        try { currentCancellable?.cancel() } catch (_: Throwable) {}
        currentCancellable = null
    }
}
