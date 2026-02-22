package com.example.powerai.domain.vision

/**
 * Domain-facing contract for Vision Boost.
 *
 * - Input is an image URI (string) so UI can pass either asset/content/file URIs.
 * - Implementation is in data layer and may use Android APIs for I/O.
 */
interface VisionBoostRepository {
    suspend fun analyzeTableToMarkdown(imageUri: String, enableDeepLogicValidation: Boolean = false): String
}
