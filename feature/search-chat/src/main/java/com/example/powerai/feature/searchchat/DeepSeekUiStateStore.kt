package com.example.powerai.feature.searchchat

import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Centrally manages UI state for the DeepSeek module.
 */
@ViewModelScoped
class DeepSeekUiStateStore
    @Inject
    constructor() {
        private val _state = MutableStateFlow(DeepSeekUiState())
        val state = _state

        fun update(transform: DeepSeekUiState.() -> DeepSeekUiState) {
            _state.update(transform)
        }
    }
