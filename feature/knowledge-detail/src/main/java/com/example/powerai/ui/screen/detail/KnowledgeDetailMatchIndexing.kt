package com.example.powerai.ui.screen.detail

internal object KnowledgeDetailMatchIndexing {
    fun prev(current: Int, total: Int): Int {
        if (total <= 0) return 0
        return if (current - 1 < 0) total - 1 else current - 1
    }

    fun next(current: Int, total: Int): Int {
        if (total <= 0) return 0
        return (current + 1) % total
    }
}
