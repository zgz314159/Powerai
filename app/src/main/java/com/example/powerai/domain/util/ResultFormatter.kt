package com.example.powerai.domain.util

import com.example.powerai.domain.model.KnowledgeItem

object ResultFormatter {
    fun formatForDisplay(results: List<KnowledgeItem>, keyword: String = ""): List<KnowledgeItem> {
        // 按类别和匹配度排序
        return results.sortedWith(compareByDescending<KnowledgeItem> {
            if (keyword.isNotBlank() && it.content.contains(keyword, ignoreCase = true)) 1 else 0
        }.thenBy { it.category })
    }
}
