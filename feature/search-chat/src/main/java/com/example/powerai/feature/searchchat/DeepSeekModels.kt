package com.example.powerai.feature.searchchat

data class CachedPromptState(
    val key: String,
    val stateData: ByteArray,
    val capturedAtEpochMs: Long
)
