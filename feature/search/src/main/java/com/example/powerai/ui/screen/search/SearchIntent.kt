package com.example.powerai.ui.screen.search

/**
 * Search 模块的用户意图
 */
sealed interface SearchIntent {
    /** 查询内容变更 */
    data class QueryChanged(val query: String) : SearchIntent

    /** 执行搜索 */
    data object Search : SearchIntent
}
