package com.example.powerai.ui.screen.hybrid

/**
 * Lightweight AI request UI state used by the main pages.
 *
 * This is intentionally small and UI-focused (no domain coupling).
 */
sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Success(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
}
