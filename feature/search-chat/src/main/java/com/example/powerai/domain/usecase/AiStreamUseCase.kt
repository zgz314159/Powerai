package com.example.powerai.domain.usecase

import com.example.powerai.core.model.util.Cancellable
import com.example.powerai.core.repository.AiStreamingRepository
import com.example.powerai.core.repository.AiStreamRequest
import com.example.powerai.core.repository.RemoteConfigRepository
import com.example.powerai.domain.model.chat.AiStreamState
import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.domain.repository.WebSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 工作流用例：封装 AI 流交互逻辑，供 ViewModel 调用
 */
class AiStreamUseCase @Inject constructor(
    private val webSearchRepository: WebSearchRepository,
    val sessionManager: ChatSessionManager,
    private val aiStreamingRepository: AiStreamingRepository,
    private val remoteConfigRepository: RemoteConfigRepository
) {
    private val _webSources = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val webSources = _webSources.asStateFlow()

    suspend fun askAiStream(
        userInput: String,
        webSearchEnabled: Boolean,
        onState: (AiStreamState) -> Unit,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit,
        onWebSources: (List<String>) -> Unit,
        updateCurrentTurnSources: (turnId: Long, sources: List<String>) -> Unit,
        getHistoryTurns: () -> List<ChatTurn>,
        updateTurnRetryAllowed: (turnId: Long, allowed: Boolean) -> Unit
    ): Cancellable {
        val turnId = System.currentTimeMillis()
        onLoading(true)
        onState(AiStreamState.Loading)

        try {
            val (url, model) = try {
                AiStreamConfigHelper.validateApiConfig(remoteConfigRepository)
            } catch (e: IllegalStateException) {
                val msg = e.message ?: "AI 配置无效"
                onError(msg)
                onState(AiStreamState.Error(msg))
                onLoading(false)
                throw e
            }

            val apiKey = remoteConfigRepository.getAiApiKey()

            val sources = AiStreamConfigHelper.performWebSearch(webSearchRepository, webSearchEnabled, userInput)
            onWebSources(sources)
            _webSources.value = sources
            if (sources.isNotEmpty()) updateCurrentTurnSources(turnId, sources)

            val historyTurns = getHistoryTurns()
            val recentTurns = historyTurns.filter { it.id != turnId }.takeLast(8)
            val apiMessages = AiStreamRequestBuilder.buildMessageFragments(recentTurns, userInput)

            val bodyJson = aiStreamingRepository.buildBody(model, apiMessages, stream = true)

            val request = AiStreamRequest(
                url = url,
                apiKey = apiKey,
                bodyJson = bodyJson,
                acceptStream = true
            )

            var cancellableRef: Cancellable? = null
            val cancellable = aiStreamingRepository.startStreaming(
                request = request,
                onOpen = {},
                onData = { raw ->
                    try {
                        val isDoneMarker = aiStreamingRepository.isDoneMarker(raw)
                        if (isDoneMarker) {
                            onLoading(false)
                            cancellableRef?.cancel()
                            return@startStreaming
                        }
                        val chunk = aiStreamingRepository.extractTextChunk(raw) ?: raw
                        onState(AiStreamState.Success(chunk))
                    } catch (_: Throwable) {}
                },
                onClosed = {
                    onLoading(false)
                    updateTurnRetryAllowed(turnId, false)
                },
                onFailure = { msg ->
                    onLoading(false)
                    onError(msg)
                    onState(AiStreamState.Error(msg))
                }
            )

            cancellableRef = cancellable
            return cancellable
        } catch (t: Throwable) {
            onLoading(false)
            onError(t.message ?: "Unknown error")
            onState(AiStreamState.Error(t.message ?: "Unknown error"))
            throw t
        }
    }

    suspend fun performStreamRequest(
        scope: CoroutineScope,
        sessions: List<ChatSession>,
        currentSessionId: Long?,
        turnId: Long,
        userInput: String,
        webSearchEnabled: Boolean,
        onState: (AiStreamState) -> Unit,
        onLoading: (Boolean) -> Unit,
        onError: (String) -> Unit,
        onWebSources: (List<String>) -> Unit,
        updateCurrentTurnSources: (turnId: Long, sources: List<String>) -> Unit,
        getHistoryTurns: () -> List<ChatTurn>,
        updateTurnRetryAllowed: (turnId: Long, allowed: Boolean) -> Unit,
        updateSessions: (List<ChatSession>) -> Unit
    ): Cancellable {
        onState.invoke(AiStreamState.Loading)

        val collator = SessionBufferCollator(scope, persistThresholdMs = 120L)
        val accBuffer = StringBuilder()

        collator.start { sampled ->
            val updated = sessionManager.updateTurnAnswer(
                sessions = sessions,
                sessionId = currentSessionId,
                turnId = turnId,
                answer = sampled,
                isError = false
            )
            updateSessions(updated)
        }

        updateTurnRetryAllowed(turnId, false)

        try {
            return askAiStream(
                userInput = userInput,
                webSearchEnabled = webSearchEnabled,
                onState = { state ->
                    if (state is AiStreamState.Success) {
                        accBuffer.append(state.text)
                        collator.update(accBuffer.toString())
                    }
                    if (state is AiStreamState.Error) {
                        collator.stop()
                    }
                    onState(state)
                },
                onLoading = { loading ->
                    if (!loading) {
                        val finalText = accBuffer.toString()
                        if (finalText.isNotBlank()) {
                            val updated = sessionManager.updateTurnAnswer(
                                sessions = sessions,
                                sessionId = currentSessionId,
                                turnId = turnId,
                                answer = finalText,
                                isError = false
                            )
                            updateSessions(updated)
                        }
                        collator.stop()
                    }
                    onLoading(loading)
                },
                onError = onError,
                onWebSources = onWebSources,
                updateCurrentTurnSources = updateCurrentTurnSources,
                getHistoryTurns = getHistoryTurns,
                updateTurnRetryAllowed = updateTurnRetryAllowed
            )
        } catch (t: Throwable) {
            collator.stop()
            throw t
        }
    }
}
