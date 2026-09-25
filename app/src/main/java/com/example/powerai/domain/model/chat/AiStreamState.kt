package com.example.powerai.domain.model.chat

sealed interface AiStreamState {
    object Idle : AiStreamState
    object Loading : AiStreamState
    data class Success(val text: String) : AiStreamState
    data class Error(val message: String) : AiStreamState
}
