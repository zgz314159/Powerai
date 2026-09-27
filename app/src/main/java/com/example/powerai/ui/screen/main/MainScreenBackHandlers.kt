package com.example.powerai.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Navigation binding for the main shell: back-press handling for the
 * database search state and for an open floating search bar.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun MainScreenBackHandlers(
    state: MainScreenState,
    dbCurrentQuery: String,
    onExitDatabaseSearch: () -> Unit,
) {
    // Database tab: if currently in a search state, back should first exit search results
    // and restore the full database list instead of exiting the app.
    BackHandler(
        enabled =
            state.selectedTab == MainBottomTab.DATABASE &&
                (dbCurrentQuery.isNotBlank() || state.searchQuery.isNotBlank()),
    ) {
        state.searchQuery = ""
        state.searchBarTab = null
        onExitDatabaseSearch()
    }

    // Back press: when a floating search bar is open and the query is empty,
    // consume back to close the search bar instead of navigating away.
    BackHandler(enabled = (state.searchBarTab != null && state.searchQuery.isBlank())) {
        state.searchBarTab = null
    }
}
