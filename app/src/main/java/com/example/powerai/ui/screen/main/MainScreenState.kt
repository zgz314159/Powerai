package com.example.powerai.ui.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Tab/pager state for the main shell: the selected bottom tab, which tab
 * owns the floating search bar, AI input focus and the shared search query.
 * All fields keep the original rememberSaveable semantics.
 */
internal class MainScreenState(
    selectedTabState: MutableState<MainBottomTab>,
    searchBarTabState: MutableState<MainBottomTab?>,
    aiInputFocusedState: MutableState<Boolean>,
    searchQueryState: MutableState<String>,
) {
    var selectedTab by selectedTabState
    var searchBarTab by searchBarTabState
    var aiInputFocused by aiInputFocusedState
    var searchQuery by searchQueryState
}

@Composable
internal fun rememberMainScreenState(): MainScreenState {
    val selectedTab = rememberSaveable { mutableStateOf(MainBottomTab.SMART) }
    val searchBarTab = rememberSaveable { mutableStateOf<MainBottomTab?>(null) }
    val aiInputFocused = rememberSaveable { mutableStateOf(false) }
    val searchQuery = rememberSaveable { mutableStateOf("") }
    val state =
        remember(selectedTab, searchBarTab, aiInputFocused, searchQuery) {
            MainScreenState(selectedTab, searchBarTab, aiInputFocused, searchQuery)
        }

    // If user switches tabs, close any open floating search bar (avoid cross-tab leaks)
    LaunchedEffect(state.selectedTab) {
        state.searchBarTab = null
    }
    return state
}

internal fun supportsLongPressSearch(tab: MainBottomTab): Boolean =
    tab == MainBottomTab.AI ||
        tab == MainBottomTab.LOCAL ||
        tab == MainBottomTab.DATABASE ||
        tab == MainBottomTab.SMART
