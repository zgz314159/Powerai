package com.example.powerai.domain.usecase

import com.example.powerai.domain.model.KnowledgeItem

class CategoryFilterUseCase {
    fun filterByCategory(items: List<KnowledgeItem>, category: String): List<KnowledgeItem> {
        return items.filter { it.category == category }
    }
}
