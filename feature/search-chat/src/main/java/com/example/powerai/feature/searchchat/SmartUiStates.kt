package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.SmartPrefillBenchmarkResult
import com.example.powerai.core.model.SmartProgressPhase
import com.example.powerai.core.model.SmartThreadBenchmarkResult

data class SmartProgressUiState(
    val phase: SmartProgressPhase = SmartProgressPhase.IDLE,
    val statusText: String = "",
    val detailText: String = "",
    val showAsActive: Boolean = false,
    val steps: List<String> = emptyList()
)

data class SmartThreadBenchmarkUiState(
    val isRunning: Boolean = false,
    val statusText: String = "",
    val results: List<SmartThreadBenchmarkResult> = emptyList()
)

data class SmartPrefillBenchmarkUiState(
    val isRunning: Boolean = false,
    val benchmarkTitle: String = "",
    val statusText: String = "",
    val results: List<SmartPrefillBenchmarkResult> = emptyList()
)
