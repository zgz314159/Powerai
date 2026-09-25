package com.example.powerai.engine.ai

internal object LlamaJniStreaming {
    @Volatile
    var streamingThinkingEnabled: Boolean = true
    @Volatile
    var callbackPreviewCount: Int = 0
    private var streamingFilterInThinkBlock: Boolean = false
    private var streamingFilterPendingThinkingText: String = ""
    private var streamingFilterPendingTagFragment: String = ""
    private var callbackLogInThinkBlock: Boolean = false
    private var callbackLogPendingThinkingText: String = ""
    private var callbackLogPendingTagFragment: String = ""

    fun resetStreamingFilters() {
        streamingFilterInThinkBlock = false
        streamingFilterPendingThinkingText = ""
        streamingFilterPendingTagFragment = ""
        callbackLogInThinkBlock = false
        callbackLogPendingThinkingText = ""
        callbackLogPendingTagFragment = ""
        callbackPreviewCount = 0
    }

    fun shouldLogStreamPreview(): Boolean {
        callbackPreviewCount += 1
        return callbackPreviewCount <= 8 || callbackPreviewCount % 16 == 0
    }

    fun sanitizeChunkForStreaming(chunk: String): String {
        if (streamingThinkingEnabled) return DeepSeekTextCodec.normalizeChunk(chunk)
        val parsed = ThinkingInterceptor.parseStreamingChunk(
            text = chunk,
            inThinkBlock = streamingFilterInThinkBlock,
            pendingThinkingText = streamingFilterPendingThinkingText,
            pendingTagFragment = streamingFilterPendingTagFragment
        )
        streamingFilterInThinkBlock = parsed.inThinkBlock
        streamingFilterPendingThinkingText = parsed.pendingThinkingText
        streamingFilterPendingTagFragment = parsed.pendingTagFragment
        return DeepSeekTextCodec.normalizeChunk(parsed.visibleText)
    }

    fun sanitizeGeneratedText(raw: String): String {
        val normalized = DeepSeekTextCodec.normalizeChunk(raw)
        if (streamingThinkingEnabled) return normalized
        return DeepSeekTextCodec.normalizeFinalAnswer(ThinkingInterceptor.stripThinkingSegments(normalized))
    }

    fun buildCallbackTokenPreview(rawText: String): String {
        val normalizedRaw = DeepSeekTextCodec.normalizeChunk(rawText)
        if (streamingThinkingEnabled) return normalizedRaw.take(120)
        val parsed = ThinkingInterceptor.parseStreamingChunk(
            text = normalizedRaw,
            inThinkBlock = callbackLogInThinkBlock,
            pendingThinkingText = callbackLogPendingThinkingText,
            pendingTagFragment = callbackLogPendingTagFragment
        )
        callbackLogInThinkBlock = parsed.inThinkBlock
        callbackLogPendingThinkingText = parsed.pendingThinkingText
        callbackLogPendingTagFragment = parsed.pendingTagFragment
        val visiblePreview = DeepSeekTextCodec.normalizeChunk(parsed.visibleText).take(120)
        return if (visiblePreview.isNotBlank()) visiblePreview else "[suppressed think token]"
    }
}
