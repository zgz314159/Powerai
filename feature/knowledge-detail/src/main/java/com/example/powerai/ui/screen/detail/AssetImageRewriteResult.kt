package com.example.powerai.ui.screen.detail

internal data class AssetImageRewriteResult(
    val text: String,
    val candidateCount: Int,
    val replacedCount: Int,
    val firstConverted: String?
)
