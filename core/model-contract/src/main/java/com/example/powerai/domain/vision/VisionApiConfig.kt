package com.example.powerai.domain.vision

/**
 * Runtime vision / Gemini gateway settings without coupling data to app [BuildConfig].
 */
data class VisionApiConfig(
    val geminiApiKey: String,
    val geminiModel: String,
    val visionApiKey: String,
    val visionModel: String,
    val visionBaseUrl: String,
    val visionPath: String,
)
