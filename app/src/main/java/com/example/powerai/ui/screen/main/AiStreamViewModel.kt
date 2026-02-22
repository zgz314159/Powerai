package com.example.powerai.ui.screen.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.BuildConfig
import com.example.powerai.data.chat.ChatHistoryStore
import com.example.powerai.data.remote.search.GoogleCustomSearchClient
import com.example.powerai.domain.ai.AiStreamingService
import com.example.powerai.domain.model.chat.ChatHistorySnapshot
import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class AiStreamViewModel @Inject constructor(
    private val googleCse: GoogleCustomSearchClient,
    private val chatHistoryStore: ChatHistoryStore,
    private val aiStreamingService: com.example.powerai.domain.ai.AiStreamingService
) : ViewModel() {
    private val TAG = "AiStreamVM"
    // Production behavior: do not rely on temporary test flags or local simulator.
    // Any explicit debug override should be provided via BuildConfig.DEBUG_STREAM_URL.

    private val _aiStreamState = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val aiStreamState: StateFlow<AiStreamState> = _aiStreamState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _askedAtMillis = MutableStateFlow<Long?>(null)
    val askedAtMillis: StateFlow<Long?> = _askedAtMillis.asStateFlow()

    private val _webSources = MutableStateFlow<List<String>>(emptyList())
    val webSources: StateFlow<List<String>> = _webSources.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()

    private val _currentTurnId = MutableStateFlow<Long?>(null)
    val currentTurnId: StateFlow<Long?> = _currentTurnId.asStateFlow()

    // Per-turn retry allowance map
    private val _turnRetryAllowed = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val turnRetryAllowed: StateFlow<Map<Long, Boolean>> = _turnRetryAllowed.asStateFlow()

    init {
        // Load persisted chat history (if any) on startup
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = chatHistoryStore.load()
                if (snapshot != null) {
                    withContext(Dispatchers.Main) {
                        _sessions.value = snapshot.sessions
                        _selectedSessionId.value = snapshot.selectedSessionId
                        _currentSessionId.value = snapshot.currentSessionId

                        // For restored history, allow retry on all existing turns by default so
                        // that the UI can always show “重新生成” for past answers.
                        val restoredTurns = snapshot.sessions.flatMap { it.turns }
                        if (restoredTurns.isNotEmpty()) {
                            _turnRetryAllowed.value = restoredTurns.associate { it.id to true }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "init load history failed: ${t.message}", t)
            }
        }
    }

    private val client: OkHttpClient by lazy {
        // Configure client for long-lived streaming connections
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Keep current streaming job so it can be cancelled when a new request starts
    private var currentJob: Job? = null

    // Keep current EventSource so it can be cancelled
    private var currentEventSource: EventSource? = null

    // Buffer flow to throttle UI updates from high-frequency stream
    private val _accBuffer = MutableStateFlow("")
    private var bufferCollectorJob: Job? = null

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _isLoading.value = false
        _errorMessage.value = throwable.message ?: "Unknown error"
    }

    /**
     * Send streaming request to DeepSeek and emit partial text as it arrives.
     * The function is cancellable and will cancel any previous running stream.
     */
    fun askAiStream(userInput: String, webSearchEnabled: Boolean) {
        // cancel previous stream if any
        currentJob?.cancel()

        Log.d(TAG, "askAiStream start enabled=$webSearchEnabled input='${userInput.take(60)}'")

        val turnId = System.currentTimeMillis()
        _askedAtMillis.value = turnId
        _currentTurnId.value = turnId

        val sessionId = _currentSessionId.value ?: _selectedSessionId.value
        if (sessionId == null) {
            newSession()
        }

        if (userInput.isNotBlank()) {
            appendTurnToCurrentSession(turnId = turnId, question = userInput)
        }

        _errorMessage.value = null
        _aiStreamState.value = AiStreamState.Loading

        currentJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            _isLoading.value = true

            val apiKey = BuildConfig.AI_API_KEY
            val baseUrl = BuildConfig.AI_BASE_URL.trim().trimEnd('/')
            if (baseUrl.isBlank()) {
                val msg = "AI 未配置：请在本地通过 Gradle 配置 BuildConfig.AI_BASE_URL（以及可选的 AI_API_KEY）。"
                _errorMessage.value = msg
                _aiStreamState.value = AiStreamState.Error(msg)
                _isLoading.value = false
                return@launch
            }
            val url = "$baseUrl/chat/completions"
            Log.d(TAG, "askAiStream start. url=$url, apiKey=***masked***")

            val mediaType = "application/json; charset=utf-8".toMediaType()

            val deviceToday = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            } catch (_: Throwable) {
                ""
            }

            val model = BuildConfig.DEEPSEEK_LOGIC_MODEL.trim().ifBlank { "deepseek-chat" }

            val cseConfigured = try { googleCse.isConfigured() } catch (_: Throwable) { false }
            Log.d(TAG, "askAiStream webSearchEnabled=$webSearchEnabled searchConfigured=$cseConfigured")

            val results = if (webSearchEnabled && cseConfigured) {
                try { googleCse.search(userInput, count = 5) } catch (_: Throwable) { emptyList() }
            } else {
                emptyList()
            }
            Log.d(TAG, "askAiStream web results=${results.size}")

            val sources = results.map { it.url }.filter { it.isNotBlank() }.distinct().take(5)
            _webSources.value = sources

            if (sources.isNotEmpty()) updateCurrentTurnSources(turnId = turnId, sources = sources)
            val searchEvidence = if (results.isNotEmpty()) {
                results.mapIndexed { idx, r ->
                    buildString {
                        append("[R")
                        append(idx + 1)
                        append("] ")
                        append(r.title)
                        if (r.snippet.isNotBlank()) {
                            append("\n")
                            append(r.snippet)
                        }
                        append("\n")
                        append(r.url)
                    }
                }.joinToString("\n\n")
            } else {
                ""
            }
            Log.d(TAG, "askAiStream evidenceLen=${searchEvidence.length}")

            val sidForHistory = _currentSessionId.value ?: _selectedSessionId.value
            val historyTurns = _sessions.value.firstOrNull { it.id == sidForHistory }?.turns.orEmpty()
            val recentTurns = historyTurns.filter { it.id != turnId }.takeLast(8)
            val apiMessages = buildList {
                recentTurns.forEach { turn ->
                    if (turn.question.isNotBlank()) {
                        add("{\"role\":\"user\",\"content\":\"${escapeJson(turn.question)}\"}")
                    }
                    if (turn.answer.isNotBlank()) {
                        add("{\"role\":\"assistant\",\"content\":\"${escapeJson(turn.answer)}\"}")
                    }
                }
                add("{\"role\":\"user\",\"content\":\"${escapeJson(userInput)}\"}")
            }
            Log.d(TAG, "askAiStream apiMessages=${apiMessages.size} historyTurns=${recentTurns.size}")

            suspend fun executeStream(allowRetry: Boolean) {
                val bodyJson = buildString {
                    append('{')
                    append("\"model\":\"")
                    append(escapeJson(model))
                    append("\",")
                    append("\"stream\":true,")
                    append("\"messages\":[")
                    append(apiMessages.joinToString(","))
                    append(']')
                    append('}')
                }

                // Debug override: prefer BuildConfig.DEBUG_STREAM_URL if present; otherwise fallback to local simulator when DEBUG.
                val debugOverrideUrl: String? = try {
                    val f = BuildConfig::class.java.getDeclaredField("DEBUG_STREAM_URL")
                    f.isAccessible = true
                    f.get(null) as? String
                } catch (_: Throwable) {
                    null
                }

                Log.d(TAG, "chooseEndpoint debug=${BuildConfig.DEBUG} override=${debugOverrideUrl ?: "<none>"} webEnabled=$webSearchEnabled results=${results.size}")

                // Selection policy:
                // - If a debug override URL is explicitly provided via BuildConfig.DEBUG_STREAM_URL in DEBUG builds, use it (GET).
                // - If search results exist, prefer production POST to feed evidence to the model.
                // - Otherwise default to production POST stream.
                val request = when {
                    BuildConfig.DEBUG && !debugOverrideUrl.isNullOrBlank() -> {
                        Log.d(TAG, "Using DEBUG SSE endpoint from BuildConfig: $debugOverrideUrl")
                        Request.Builder()
                            .url(debugOverrideUrl)
                            .addHeader("Accept", "text/event-stream")
                            .get()
                            .build()
                    }
                    results.isNotEmpty() -> {
                        Log.d(TAG, "Using production SSE endpoint because search results present (size=${results.size})")
                        Request.Builder()
                            .url(url)
                            .addHeader("Authorization", "Bearer $apiKey")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Accept", "text/event-stream")
                            .post(bodyJson.toRequestBody(mediaType))
                            .build()
                    }
                    else -> {
                        Request.Builder()
                            .url(url)
                            .addHeader("Authorization", "Bearer $apiKey")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Accept", "text/event-stream")
                            .post(bodyJson.toRequestBody(mediaType))
                            .build()
                    }
                }

                // Log request body summary for debugging (do not print API key)
                try {
                    fun sha1Hex(s: String): String {
                        val md = MessageDigest.getInstance("SHA-1")
                        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
                        return bytes.joinToString("") { "%02x".format(it) }
                    }
                    Log.d(TAG, "SSE request body len=${bodyJson.length} sha1=${sha1Hex(bodyJson)} preview=${bodyJson.take(1000)}")
                } catch (t: Throwable) {
                    Log.d(TAG, "SSE request body logging failed: ${t.message}")
                }

                // Use OkHttp SSE EventSource for streaming event-based responses
                val sb = StringBuilder()

                // clear any previous buffer and start buffer collector which conflates updates
                _accBuffer.value = ""
                bufferCollectorJob?.cancel()
                // mark retry allowance for this turn as false initially
                _turnRetryAllowed.value = _turnRetryAllowed.value + (turnId to false)
                bufferCollectorJob = viewModelScope.launch(Dispatchers.Main) {
                    var lastPersistAt = 0L
                    _accBuffer.collect { sampled ->
                        _aiStreamState.value = AiStreamState.Success(sampled)
                        val now = System.currentTimeMillis()
                        // persist occasionally (throttle DB writes)
                        if (now - lastPersistAt > 500) {
                            updateCurrentTurnAnswer(turnId = turnId, answer = sampled, isError = false)
                            lastPersistAt = now
                        }
                    }
                }

                // Cancel previous EventSource if active
                try {
                    currentEventSource?.cancel()
                } catch (_: Throwable) {}

                val done = CompletableDeferred<Unit>()
                // flag to indicate we intentionally cancelled the EventSource after receiving a done marker
                var cancelledByDone = false

                // Start streaming via service; service will call our callbacks on EventSource thread.
                currentEventSource = aiStreamingService.startStreaming(
                    request = request,
                    onOpen = { response ->
                        try { Log.d(TAG, "SSE opened responseCode=${response.code} headers=${response.headers}") } catch (_: Throwable) { Log.d(TAG, "SSE opened") }
                    },
                    onData = { raw ->
                        try {
                            // detect explicit done markers in the chunk
                            val isDoneMarker = aiStreamingService.isSseDoneMarker(raw)
                            if (isDoneMarker) {
                                try { Log.d(TAG, "SSE done marker raw data=${raw.take(1000)}") } catch (_: Throwable) {}
                                viewModelScope.launch { _isLoading.value = false }
                                cancelledByDone = true
                                try { currentEventSource?.cancel() } catch (_: Throwable) {}
                                Log.d(TAG, "SSE received done marker; cancelling eventSource")
                                return@startStreaming
                            }

                            val chunk = aiStreamingService.extractTextChunk(raw) ?: raw
                            sb.append(chunk)
                            _accBuffer.value = sb.toString()
                            val size = chunk.length
                            Log.d(TAG, "SSE chunk received size=$size")
                        } catch (t: Throwable) {
                            Log.d(TAG, "SSE onData error: ${t.message}")
                        }
                    },
                    onClosed = {
                        Log.d(TAG, "SSE closed")
                        viewModelScope.launch { _isLoading.value = false }
                        bufferCollectorJob?.cancel()
                        _aiStreamState.value = AiStreamState.Success(sb.toString())
                        viewModelScope.launch {
                            updateCurrentTurnAnswer(turnId = turnId, answer = sb.toString(), isError = false)
                            try { persistHistoryBlocking() } catch (t: Throwable) { Log.d(TAG, "persistHistoryBlocking failed: ${t.message}") }
                        }
                        _turnRetryAllowed.value = _turnRetryAllowed.value + (turnId to allowRetry)
                        if (!done.isCompleted) done.complete(Unit)
                    },
                    onFailure = { msg, t ->
                        if (cancelledByDone) {
                            Log.v(TAG, "SSE failure after intentional cancel: ${t?.message}")
                            bufferCollectorJob?.cancel()
                            if (!done.isCompleted) done.complete(Unit)
                            return@startStreaming
                        }
                        Log.d(TAG, "SSE failure: $msg", t)
                        viewModelScope.launch { _isLoading.value = false }
                        bufferCollectorJob?.cancel()
                        _accBuffer.value = msg
                        _errorMessage.value = msg
                        _aiStreamState.value = AiStreamState.Error(msg)
                        viewModelScope.launch {
                            updateCurrentTurnAnswer(turnId = turnId, answer = msg, isError = true)
                            try { persistHistoryBlocking() } catch (t2: Throwable) { Log.d(TAG, "persistHistoryBlocking failed after failure: ${t2.message}") }
                        }
                        _turnRetryAllowed.value = _turnRetryAllowed.value + (turnId to allowRetry)
                        if (!done.isCompleted) done.complete(Unit)
                    }
                )

                try {
                    // wait until closed or failed
                    done.await()
                } catch (e: Exception) {
                    val msg = e.message ?: "SSE start failed"
                    Log.d(TAG, "SSE exception starting: $msg")
                    bufferCollectorJob?.cancel()
                    _errorMessage.value = msg
                    _aiStreamState.value = AiStreamState.Error(msg)
                    updateCurrentTurnAnswer(turnId = turnId, answer = msg, isError = true)
                    persistHistory()
                    _isLoading.value = false
                } finally {
                    try { currentEventSource?.cancel() } catch (_: Throwable) {}
                    currentEventSource = null
                }
            }

            Log.d(TAG, "askAiStream prompt=passthrough webEvidenceLen=${searchEvidence.length} date=$deviceToday")

            executeStream(allowRetry = true)
        }
    }

    fun selectSession(sessionId: Long) {
        _selectedSessionId.value = sessionId
        _currentSessionId.value = sessionId
        persistHistory()
    }

    fun newSession() {
        val now = System.currentTimeMillis()
        val session = ChatSession(
            id = now,
            title = "新对话",
            turns = emptyList(),
            createdAtMillis = now,
            updatedAtMillis = now
        )
        _sessions.value = (listOf(session) + _sessions.value).take(50)
        _selectedSessionId.value = session.id
        _currentSessionId.value = session.id
        persistHistory()
    }

    private fun appendTurnToCurrentSession(turnId: Long, question: String) {
        val now = System.currentTimeMillis()
        val sid = _currentSessionId.value ?: run {
            newSession()
            _currentSessionId.value
        } ?: return

        val turn = ChatTurn(
            id = turnId,
            question = question,
            answer = "",
            askedAtMillis = now
        )

        _sessions.value = _sessions.value.map { s ->
            if (s.id != sid) return@map s
            val wasEmpty = s.turns.isEmpty()
            val newTitle = if (wasEmpty && s.title == "新对话") question else s.title
            s.copy(
                title = newTitle,
                turns = (s.turns + turn).takeLast(200),
                updatedAtMillis = now
            )
        }
        persistHistory()
    }

    private fun updateCurrentTurnSources(turnId: Long, sources: List<String>) {
        val sid = _currentSessionId.value ?: return
        _sessions.value = _sessions.value.map { s ->
            if (s.id != sid) return@map s
            s.copy(
                turns = s.turns.map { t -> if (t.id == turnId) t.copy(sources = sources) else t },
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    private fun updateCurrentTurnAnswer(turnId: Long, answer: String, isError: Boolean) {
        val sid = _currentSessionId.value ?: return
        _sessions.value = _sessions.value.map { s ->
            if (s.id != sid) return@map s
            s.copy(
                turns = s.turns.map { t -> if (t.id == turnId) t.copy(answer = answer, isError = isError) else t },
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    private fun persistHistory() {
        val snapshot = ChatHistorySnapshot(
            sessions = _sessions.value,
            selectedSessionId = _selectedSessionId.value,
            currentSessionId = _currentSessionId.value
        )
        viewModelScope.launch(Dispatchers.IO) {
            chatHistoryStore.save(snapshot)
        }
    }

    // Strong sync: suspend and perform immediate save on IO dispatcher.
    private suspend fun persistHistoryBlocking() {
        val snapshot = ChatHistorySnapshot(
            sessions = _sessions.value,
            selectedSessionId = _selectedSessionId.value,
            currentSessionId = _currentSessionId.value
        )
        withContext(Dispatchers.IO) {
            chatHistoryStore.save(snapshot)
        }
    }

    fun stopStream() {
        currentJob?.cancel()
        currentJob = null
        _isLoading.value = false
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
    }

    // Basic JSON escaper for string values
    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    // Try to pull out the text chunk from a JSON payload.
    // Supports OpenAI-style SSE fragments (choices[].delta.content, choices[].message.content)
    // and falls back to simple regex extraction for non-JSON fragments.
    private fun extractTextChunk(jsonLike: String): String? {
        val trimmed = jsonLike.trim()
        try {
            val rootElem = JsonParser().parse(trimmed)
            if (rootElem.isJsonObject) {
                val root = rootElem.asJsonObject
                if (root.has("choices") && root.get("choices").isJsonArray) {
                    val arr = root.getAsJsonArray("choices")
                    for (el in arr) {
                        try {
                            val choice = el.asJsonObject
                            if (choice.has("delta") && choice.get("delta").isJsonObject) {
                                val delta = choice.getAsJsonObject("delta")
                                if (delta.has("content")) return unescapeJson(delta.get("content").asString)
                            }
                            if (choice.has("message") && choice.get("message").isJsonObject) {
                                val msg = choice.getAsJsonObject("message")
                                if (msg.has("content")) return unescapeJson(msg.get("content").asString)
                            }
                            if (choice.has("text")) return unescapeJson(choice.get("text").asString)
                        } catch (_: Throwable) {
                        }
                    }
                }
                if (root.has("content")) return unescapeJson(root.get("content").asString)
                if (root.has("text")) return unescapeJson(root.get("text").asString)
            }
        } catch (_: Throwable) {
            // fall through to regex fallback
        }

        // regex fallback for non-JSON or partial chunks
        val contentRegex = "\\\"content\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"".toRegex()
        val m = contentRegex.find(jsonLike)
        if (m != null) {
            return unescapeJson(m.groupValues[1])
        }
        val textRegex = "\\\"text\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"".toRegex()
        val m2 = textRegex.find(jsonLike)
        if (m2 != null) {
            return unescapeJson(m2.groupValues[1])
        }
        return null
    }

    private fun unescapeJson(s: String): String {
        return s.replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun isSseDoneMarker(data: String): Boolean {
        if (data.contains("[DONE]", ignoreCase = true)) return true

        val trimmed = data.trim()
        if (trimmed.isBlank()) return false

        return try {
            val root = JsonParser().parse(trimmed)
            if (!root.isJsonObject) return false
            val obj = root.asJsonObject
            if (!obj.has("choices") || !obj.get("choices").isJsonArray) return false

            obj.getAsJsonArray("choices").any { choiceElem ->
                val choiceObj: JsonObject = try {
                    choiceElem.asJsonObject
                } catch (_: Throwable) {
                    return@any false
                }
                if (!choiceObj.has("finish_reason")) return@any false
                val finish = choiceObj.get("finish_reason")
                !finish.isJsonNull && finish.toString().trim('"').isNotBlank()
            }
        } catch (_: Throwable) {
            false
        }
    }

    private data class OpenAiError(val message: String?, val type: String?, val code: String?)

    private fun parseOpenAiStyleError(raw: String): OpenAiError? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{") || !trimmed.contains("\"error\"")) return null
        return try {
            val root = JsonParser().parse(trimmed).asJsonObject
            val err = root.getAsJsonObject("error") ?: return null
            OpenAiError(
                message = err.get("message")?.asString,
                type = err.get("type")?.asString,
                code = err.get("code")?.asString
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun isContentRiskError(message: String): Boolean {
        val m = message.lowercase(Locale.US)
        return m.contains("content exists risk") || m.contains("invalid_request_error")
    }
}
