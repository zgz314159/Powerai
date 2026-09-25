package com.example.powerai.core.model

enum class SmartAnswerMode(
    val label: String,
    val maxTokens: Int,
    val evidenceLimit: Int,
    val thinkingEnabled: Boolean
) {
    FAST(label = "\u5feb\u901f\u56de\u7b54", maxTokens = 160, evidenceLimit = 1, thinkingEnabled = false),
    DEEP(label = "\u6df1\u5ea6\u601d\u8003", maxTokens = 768, evidenceLimit = 3, thinkingEnabled = true)
}

enum class SmartBackendMode(
    val label: String,
    val useGpu: Boolean
) {
    CPU(label = "CPU\u7a33\u5b9a", useGpu = false),
    VULKAN(label = "Vulkan", useGpu = true)
}

enum class SmartThreadPreset(
    val label: String,
    val threadCount: Int
) {
    TWO(label = "2\u7ebf\u7a0b", threadCount = 2),
    FOUR(label = "4\u7ebf\u7a0b", threadCount = 4),
    SIX(label = "6\u7ebf\u7a0b", threadCount = 6),
    EIGHT(label = "8\u7ebf\u7a0b", threadCount = 8)
}

enum class SmartBatchSizePreset(
    val label: String,
    val tokenCount: Int
) {
    B32(label = "B32", tokenCount = 32),
    B64(label = "B64", tokenCount = 64),
    B128(label = "B128", tokenCount = 128),
    B256(label = "B256", tokenCount = 256)
}

enum class SmartBatchThreadPreset(
    val label: String,
    val threadCount: Int
) {
    TWO(label = "TB2", threadCount = 2),
    FOUR(label = "TB4", threadCount = 4),
    SIX(label = "TB6", threadCount = 6),
    EIGHT(label = "TB8", threadCount = 8)
}

enum class SmartProgressPhase(val label: String) {
    IDLE("\u7a7a\u95f2"),
    LOADING("\u52a0\u8f7d\u6a21\u578b"),
    RETRIEVING("\u641c\u7d22\u77e5\u8bc6"),
    BUILDING_PROMPT("\u7ec4\u7ec7\u95ee\u9898"),
    WAITING_FIRST_TOKEN("\u7b49\u5f85\u9996\u5b57"),
    THINKING("\u6a21\u578b\u601d\u8003\u4e2d"),
    ANSWERING("\u8f93\u51fa\u7b54\u6848"),
    STOPPING("\u505c\u6b62\u4e2d"),
    COMPLETED("\u5b8c\u6210"),
    CANCELLED("\u5df2\u505c\u6b62\u751f\u6210"),
    ERROR("\u9519\u8bef")
}

data class SmartGenerationRunResult(
    val status: String,
    val firstTokenLatencyMs: Long? = null,
    val answerStartLatencyMs: Long? = null,
    val errorMessage: String? = null
)

data class SmartStageMetrics(
    val retrievalMs: Long? = null,
    val promptBuildMs: Long? = null,
    val decodeCharsPerSecond: Double? = null,
    val evidenceCount: Int = 0,
    val promptChars: Int = 0,
    val modeLabel: String = "FAST",
    val backendLabel: String = "CPU",
    val threadLabel: String = "FOUR",
    val batchLabel: String = "B128",
    val batchThreadLabel: String = "FOUR",
    val prefixReuseEnabled: Boolean = false
)

data class SmartThreadBenchmarkResult(
    val threadLabel: String,
    val status: String,
    val firstTokenLatencyMs: Long? = null,
    val answerStartLatencyMs: Long? = null,
    val errorMessage: String? = null
)

data class SmartPrefillBenchmarkResult(
    val dimensionLabel: String,
    val status: String,
    val firstTokenLatencyMs: Long? = null,
    val answerStartLatencyMs: Long? = null,
    val errorMessage: String? = null
)