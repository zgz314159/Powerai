package com.example.powerai.ui.screen.database

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.powerai.navigation.Screen

/**
 * Entry point of the database screen: ViewModel/state subscription, scroll
 * anchors, focus effects, event wiring and the top-level scaffold. Rendering
 * lives in [DatabaseContent], side effects in [DatabaseFocusEffects] and pure
 * lookup logic in [DatabaseNavigation].
 */
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(
    navController: NavHostController,
    viewModel: DatabaseViewModel = hiltViewModel(),
    searchQuery: String = "",
    onQueryChange: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onClear: () -> Unit = {},
    showTopSearchBar: Boolean = true,
    showTopBar: Boolean = false,
    isActive: Boolean = true,
    innerPadding: PaddingValues = PaddingValues(),
) {
    val logTag = "PowerAiDbDebug"
    val listState =
        rememberSaveable(saver = LazyListState.Saver) {
            LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
        }

    // Load data only when this screen is active (visible). This reduces work during
    // tab switches and avoids UI jank caused by composing heavy lists while not visible.
    LaunchedEffect(isActive) {
        if (isActive) viewModel.ensureLoaded()
    }

    val uiState by viewModel.uiState.collectAsState()
    val collapsedGroupKeys = uiState.collapsedGroupKeys
    val isSearching = searchQuery.trim().isNotBlank()
    val groupAnchors =
        remember(uiState.groups, collapsedGroupKeys) {
            buildDatabaseAnchors(
                groups = uiState.groups,
                collapsedGroupKeys = collapsedGroupKeys,
            )
        }

    LaunchedEffect(uiState.isLoading, uiState.errorMessage, uiState.groups.size, searchQuery, isActive) {
        val sample = uiState.groups.take(3).joinToString { it.fileName }
        Log.d(
            logTag,
            "DatabaseScreen state isActive=$isActive loading=${uiState.isLoading} " +
                "error=${uiState.errorMessage} groups=${uiState.groups.size} " +
                "query=${searchQuery.trim()} sample=$sample",
        )
    }

    DatabaseFocusEffects(
        uiState = uiState,
        listState = listState,
        collapsedGroupKeys = collapsedGroupKeys,
        isSearching = isSearching,
        searchQuery = searchQuery,
        viewModel = viewModel,
    )

    val content: @Composable (PaddingValues) -> Unit = { inner ->
        DatabaseContent(
            inner = inner,
            uiState = uiState,
            listState = listState,
            groupAnchors = groupAnchors,
            searchQuery = searchQuery,
            showTopSearchBar = showTopSearchBar,
            bottomInset = innerPadding.calculateBottomPadding(),
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            onToggleGroup = { viewModel.toggleCollapsedGroupKey(it) },
            onItemClick = { item ->
                viewModel.setSelectedItem(item.id)
                val encoded = android.net.Uri.encode(searchQuery)
                navController.navigate(Screen.Detail.createRoute(item.id, encoded, null, null))
            },
        )
    }

    if (showTopBar) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("数据") },
                )
            },
        ) { inner ->
            content(inner)
        }
    } else {
        // When nested inside MainScreen we do not want an extra Scaffold/top bar.
        content(PaddingValues())
    }
}
