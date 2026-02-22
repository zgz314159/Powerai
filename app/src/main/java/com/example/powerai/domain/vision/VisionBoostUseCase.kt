package com.example.powerai.domain.vision

import javax.inject.Inject

class VisionBoostUseCase @Inject constructor(
    private val repo: VisionBoostRepository
) {
    suspend fun invoke(imageUri: String, enableDeepLogicValidation: Boolean = false): String {
        return repo.analyzeTableToMarkdown(imageUri, enableDeepLogicValidation)
    }
}
