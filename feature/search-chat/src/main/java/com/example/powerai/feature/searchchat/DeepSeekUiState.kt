package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.SmartAnswerMode
import com.example.powerai.core.model.SmartBackendMode
import com.example.powerai.core.model.SmartBatchSizePreset
import com.example.powerai.core.model.SmartBatchThreadPreset
import com.example.powerai.core.model.SmartGenerationRunResult
import com.example.powerai.core.model.SmartPrefillBenchmarkResult
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.model.SmartStageMetrics
import com.example.powerai.core.model.SmartThreadBenchmarkResult
import com.example.powerai.core.model.SmartThreadPreset
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.GenerationMetrics

data class DeepSeekUiState(
    val isLoading: Boolean = false,
    val modelLoaded: Boolean = false,
    val modelPath: String = "",
    val thinking: List<String> = emptyList(),
    val thinkingPreview: String = "",
    val result: String = "",
    val smartReferences: List<KnowledgeItem> = emptyList(),
    val askedAtMillis: Long? = null,
    val generationMetrics: GenerationMetrics = GenerationMetrics(),
    val answerMode: SmartAnswerMode = SmartAnswerMode.FAST,
    val backendMode: SmartBackendMode = SmartBackendMode.CPU,
    val threadPreset: SmartThreadPreset = SmartThreadPreset.FOUR,
    val batchSizePreset: SmartBatchSizePreset = SmartBatchSizePreset.B128,
    val batchThreadPreset: SmartBatchThreadPreset = SmartBatchThreadPreset.FOUR,
    val prefixReuseEnabled: Boolean = true,
    val stateSnapshotReuseEnabled: Boolean = false,
    val stateSnapshotProbeRunning: Boolean = false,
    val stateSnapshotStatus: String = "",
    val stageMetrics: SmartStageMetrics = SmartStageMetrics(),
    val progressState: SmartProgressUiState = SmartProgressUiState(),
    val threadBenchmarkState: SmartThreadBenchmarkUiState = SmartThreadBenchmarkUiState(),
    val prefillBenchmarkState: SmartPrefillBenchmarkUiState = SmartPrefillBenchmarkUiState()
)
