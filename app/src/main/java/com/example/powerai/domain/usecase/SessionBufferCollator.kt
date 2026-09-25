package com.example.powerai.domain.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Helper responsible for accumulating streaming text fragments and periodically
 * invoking a persistence callback. The calling code provides a coroutine scope
 * (typically a ViewModel scope) so that the collector can be cancelled along
 * with the caller, and a lambda which is triggered when the accumulated value
 * should be applied (for example by updating the current chat session).
 *
 * This class encapsulates what used to be ad‑hoc buffering logic inside
 * [AiStreamUseCase.performStreamRequest] so that the use case itself can stay
 * focused on orchestration and network behaviour.
 */
class SessionBufferCollator(
    private val scope: CoroutineScope,
    private val persistThresholdMs: Long = 500L
) {
    /**
     * Mutable flow that callers should push new text values into. Each time the
     * flow emits, the collator will notify [onPersist] according to the timing
     * rules described above.
     */
    val buffer: MutableStateFlow<String> = MutableStateFlow("")

    private var collectorJob: Job? = null

    /**
     * Start collecting the [buffer]. The returned [buffer] instance is the same
     * property above, provided for convenience.
     */
    fun start(onPersist: (String) -> Unit): MutableStateFlow<String> {
        // guard against duplicate starts
        if (collectorJob != null) return buffer

        collectorJob = scope.launch {
            var lastPersistAt = 0L
            var firstPersisted = false

            buffer.collect { sampled ->
                if (sampled.isBlank()) return@collect
                val now = System.currentTimeMillis()
                if (!firstPersisted || now - lastPersistAt > persistThresholdMs) {
                    onPersist(sampled)
                    lastPersistAt = now
                    firstPersisted = true
                }
            }
        }
        return buffer
    }

    /** Push a new sampled value into the buffer. */
    fun update(value: String) {
        buffer.value = value
    }

    /**
     * Stops the collector if it is running. Safe to call multiple times.
     */
    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
    }
}
