package com.example.powerai.ui.screen.main

import androidx.lifecycle.viewModelScope
import com.example.powerai.domain.model.chat.AiStreamState
import com.example.powerai.domain.model.chat.ChatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiStreamUiState(
    val aiStreamState: AiStreamState = AiStreamState.Idle,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val askedAtMillis: Long? = null,
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Long? = null,
    val selectedSessionId: Long? = null,
    val currentTurnId: Long? = null,
    val turnRetryAllowed: Map<Long, Boolean> = emptyMap()
)

@HiltViewModel
class AiStreamViewModel @Inject constructor(
    private val chatHistoryStore: com.example.powerai.data.chat.ChatHistoryStore,
    private val streamUseCase: com.example.powerai.domain.usecase.AiStreamUseCase
) : com.example.powerai.ui.mvi.BaseMviViewModel<AiStreamIntent, AiStreamUiState, Nothing>(
    initialState = AiStreamUiState()
) {

    private var persistDebounceJob: Job? = null

    internal var orchestrator: AiStreamOrchestrator? = null

    private val actualOrchestrator: AiStreamOrchestrator by lazy {
        orchestrator ?: AiStreamOrchestrator(streamUseCase, viewModelScope)
    }

    val webSources: StateFlow<List<String>> = streamUseCase.webSources

    override fun onIntent(intent: AiStreamIntent) {
        when (intent) {
            is AiStreamIntent.AskAiStream -> askAiStream(intent.userInput, intent.webSearchEnabled)
            is AiStreamIntent.StopStream -> stopStream()
            is AiStreamIntent.SelectSession -> selectSession(intent.sessionId)
            is AiStreamIntent.NewSession -> newSession()
        }
    }

    init {
        viewModelScope.launch {
            try {
                val snapshot = streamUseCase.sessionManager.loadHistory()
                if (snapshot != null) {
                    updateState {
                        copy(
                            sessions = snapshot.sessions,
                            selectedSessionId = snapshot.selectedSessionId,
                            currentSessionId = snapshot.currentSessionId,
                            turnRetryAllowed = snapshot.sessions.flatMap { it.turns }.associate { it.id to true }
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun askAiStream(userInput: String, webSearchEnabled: Boolean) {
        val normalizedInput = userInput.trim()
        if (normalizedInput.isBlank()) return

        updateState { copy(isLoading = false) }

        val turnId = System.currentTimeMillis()
        updateState { copy(askedAtMillis = turnId, currentTurnId = turnId) }

        val (newSessions, newSid) = streamUseCase.sessionManager.appendTurn(
            sessions = currentState.sessions,
            currentSessionId = currentState.currentSessionId,
            turnId = turnId,
            question = normalizedInput
        )
        updateState { copy(sessions = newSessions, selectedSessionId = newSid, currentSessionId = newSid) }

        viewModelScope.launch {
            try {
                streamUseCase.sessionManager.persistHistory(
                    sessions = newSessions,
                    selectedSessionId = newSid,
                    currentSessionId = newSid
                )
            } catch (_: Throwable) {
            }
        }

        updateState { copy(errorMessage = null, aiStreamState = AiStreamState.Loading) }

        actualOrchestrator.startStream(
            sessions = currentState.sessions,
            currentSessionId = currentState.currentSessionId,
            turnId = turnId,
            userInput = normalizedInput,
            webSearchEnabled = webSearchEnabled,
            onState = { state ->
                updateState { copy(aiStreamState = state) }
            },
            onLoading = {
                updateState { copy(isLoading = it) }
                if (!it) {
                    persistDebounceJob?.cancel()
                    viewModelScope.launch {
                        try {
                            streamUseCase.sessionManager.persistHistory(
                                sessions = currentState.sessions,
                                selectedSessionId = currentState.selectedSessionId,
                                currentSessionId = currentState.currentSessionId
                            )
                        } catch (_: Throwable) {
                        }
                    }
                }
            },
            onError = { msg ->
                updateState { copy(errorMessage = msg) }
            },
            updateCurrentTurnSources = { id, sources ->
                viewModelScope.launch {
                    val newSessions = streamUseCase.sessionManager.updateTurnSourcesAndPersist(
                        sessions = currentState.sessions,
                        sessionId = currentState.currentSessionId,
                        turnId = id,
                        sources = sources
                    )
                    updateState { copy(sessions = newSessions) }
                }
            },
            getHistoryTurns = { currentState.sessions.flatMap { it.turns } },
            updateTurnRetryAllowed = { id, allowed ->
                updateState { copy(turnRetryAllowed = turnRetryAllowed + (id to allowed)) }
            },
            updateSessions = { newSessions ->
                updateState { copy(sessions = newSessions) }
                persistDebounceJob?.cancel()
                persistDebounceJob = viewModelScope.launch {
                    delay(320L)
                    try {
                        streamUseCase.sessionManager.persistHistory(
                            sessions = currentState.sessions,
                            selectedSessionId = currentState.selectedSessionId,
                            currentSessionId = currentState.currentSessionId
                        )
                    } catch (_: Throwable) {
                    }
                }
            }
        )
    }

    private fun selectSession(sessionId: Long) {
        updateState { copy(selectedSessionId = sessionId, currentSessionId = sessionId) }
        viewModelScope.launch {
            streamUseCase.sessionManager.selectSessionAndPersist(
                sessions = currentState.sessions,
                sessionId = sessionId
            )
        }
    }

    private fun newSession() {
        viewModelScope.launch {
            val (newSessions, newSid) = streamUseCase.sessionManager.createNewSessionAndPersist(
                currentState.sessions
            )
            updateState { copy(sessions = newSessions, selectedSessionId = newSid, currentSessionId = newSid) }
        }
    }

    private fun stopStream() {
        persistDebounceJob?.cancel()
        actualOrchestrator.stopStream()
        updateState { copy(isLoading = false) }
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
    }
}
