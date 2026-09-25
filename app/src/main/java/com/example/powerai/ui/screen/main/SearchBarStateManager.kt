package com.example.powerai.ui.screen.main

import androidx.compose.runtime.Stable

/**
 * 搜索框状态管理容
 * 
 * 职责
 * - 管理搜索框的输入状态（searchQuery
 * - 管理搜索框的可见性（searchBarTab、selectedTab的匹配）
 * - 清晰的状态转换逻辑
 * 
 * 这个类遵循SRP：只负责搜索框状态管理，不处理业务逻辑
 */
@Stable
data class SearchBarState(
    val searchQuery: String = "",
    val searchBarTab: MainBottomTab? = null,
    val selectedTab: MainBottomTab = MainBottomTab.SMART
) {
    /**
     * 搜索框是否应该显
     */
    val isSearchBarVisible: Boolean
        get() = searchBarTab == selectedTab

    /**
     * 更新搜索
     */
    fun updateQuery(newQuery: String): SearchBarState =
        copy(searchQuery = newQuery)

    /**
     * 清空搜索
     */
    fun clearQuery(): SearchBarState =
        copy(searchQuery = "")

    /**
     * 打开特定tab的搜索框
     */
    fun openSearchBar(tab: MainBottomTab): SearchBarState =
        copy(searchBarTab = tab)

    /**
     * 关闭搜索
     */
    fun closeSearchBar(): SearchBarState =
        copy(searchBarTab = null)

    /**
     * 切换tab时清除搜索框（防止跨tab泄露
     */
    fun switchTab(newTab: MainBottomTab): SearchBarState =
        copy(selectedTab = newTab, searchBarTab = null)

    /**
     * 搜索后的状态转换：清空输入，关闭搜索框
     */
    fun afterSearch(): SearchBarState =
        copy(searchQuery = "", searchBarTab = null)
}
