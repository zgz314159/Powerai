package com.example.powerai.ui.screen.main

sealed class AiStreamState {
    object Idle : AiStreamState()
    object Loading : AiStreamState()
    data class Success(val text: String) : AiStreamState()
    data class Error(val message: String) : AiStreamState()
}
