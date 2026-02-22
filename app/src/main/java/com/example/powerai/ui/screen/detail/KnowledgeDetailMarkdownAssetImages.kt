package com.example.powerai.ui.screen.detail

import android.content.Context
import android.util.Log

// Replace various asset path patterns with markdown image syntax if they look like image files
internal fun convertAssetPathsToImages(markdown: String, context: Context): String {
    val result = KnowledgeDetailMarkdownAssetImagesRewriter.rewrite(markdown, context)

    if (result.candidateCount == 0) return markdown

    Log.d(
        "KnowledgeDetailScreen",
        "convertAssetPathsToImages: found ${result.candidateCount} candidate(s), replaced=${result.replacedCount}"
    )
    if (result.firstConverted != null) {
        Log.i("KnowledgeDetailScreen", "convertAssetPathsToImages: firstConverted=${result.firstConverted}")
    } else {
        // No replacements performed — help debug by checking for broken markdown markers
        val hasBang = markdown.contains("!")
        val hasSquare = markdown.contains("[") && markdown.contains("]")
        Log.w(
            "KnowledgeDetailScreen",
            "convertAssetPathsToImages: no conversions performed; hasBang=$hasBang hasSquare=$hasSquare"
        )
    }

    return result.text
}
