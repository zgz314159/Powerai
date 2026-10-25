package com.example.powerai.ui.screen.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.powerai.ui.component.SearchBar
import com.example.powerai.ui.screen.database.DatabaseScreen
import com.example.powerai.ui.screen.database.DatabaseViewModel
import kotlinx.coroutines.launch

/**
 * Database tab: [DatabaseScreen] plus a source-file drawer with rebuild / user-KB controls.
 *
 * [onNavigateToKbImport] is provided by the app shell so this feature does not depend on
 * app navigation types.
 */
@Composable
fun DatabaseTabContent(
    navController: NavHostController,
    dbViewModel: DatabaseViewModel,
    dbDrawerState: DrawerState,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    onNavigateToKbImport: () -> Unit,
    innerPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(),
) {
    val dbUiState by dbViewModel.uiState.collectAsState()
    val currentDbQuery = dbUiState.currentQuery
    val importNavScope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(currentDbQuery) {
        if (currentDbQuery != searchQuery) {
            onQueryChange(currentDbQuery)
        }
    }

    ModalNavigationDrawer(
        drawerState = dbDrawerState,
        scrimColor = Color.Black.copy(alpha = 0.18f),
        drawerContent = {
            DatabaseTabDrawer(
                dbViewModel = dbViewModel,
                dbDrawerState = dbDrawerState,
                currentDbQuery = currentDbQuery,
                onQueryChange = onQueryChange,
                onImportDirectory = {
                    importNavScope.launch { dbDrawerState.close() }
                    onNavigateToKbImport()
                },
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DatabaseScreen(
                navController = navController,
                viewModel = dbViewModel,
                searchQuery = searchQuery,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                onClear = onClear,
                showTopSearchBar = false,
                isActive = true,
                innerPadding = innerPadding,
            )

            if (showSearchBar) {
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    SearchBar(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        onSearch = {
                            val normalized = searchQuery.trim()
                            if (normalized.isBlank()) return@SearchBar
                            if (normalized != searchQuery) onQueryChange(normalized)
                            onSearch()
                            onShowSearchBarChange(false)
                        },
                        onClear = onClear,
                        label = "搜索数据",
                        placeholder = "搜索数据",
                        autoFocus = showSearchBar,
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseTabDrawer(
    dbViewModel: DatabaseViewModel,
    dbDrawerState: DrawerState,
    currentDbQuery: String,
    onQueryChange: (String) -> Unit,
    onImportDirectory: () -> Unit,
) {
    val localCoroutineScope = rememberCoroutineScope()
    val dbUiState by dbViewModel.uiState.collectAsState()
    val directoryGroups by dbViewModel.directoryGroups.collectAsState()
    val importDiagnostics by dbViewModel.importDiagnostics.collectAsState()
    val importProgress by dbViewModel.importProgress.collectAsState()
    val kbRebuildState by dbViewModel.kbRebuildState.collectAsState()
    val userPackages by dbViewModel.userPackages.collectAsState()
    val updateDirectoryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) dbViewModel.updateUserPackage(uri)
        }
    val drawerGroups = if (directoryGroups.isNotEmpty()) directoryGroups else dbUiState.groups
    val drawerLoading = dbUiState.isLoading && drawerGroups.isEmpty()
    DatabaseHistoryDrawerContent(
        groups = drawerGroups,
        currentQuery = currentDbQuery,
        isLoading = drawerLoading,
        diagnostics = importDiagnostics,
        progress = importProgress,
        rebuildUi =
            KbRebuildUiState(
                state = kbRebuildState,
                confirmVisible = dbUiState.isRebuildConfirmVisible,
                clearAllStep = dbUiState.clearAllStep,
                onRequest = dbViewModel::requestRebuildKnowledgeBase,
                onConfirm = dbViewModel::confirmRebuildKnowledgeBase,
                onCancel = dbViewModel::cancelRebuildKnowledgeBase,
                onRequestClearAll = dbViewModel::requestClearAllKnowledgeBase,
                onContinueClearAll = dbViewModel::continueClearAllKnowledgeBase,
                onConfirmClearAll = dbViewModel::confirmClearAllKnowledgeBase,
                onCancelClearAll = dbViewModel::cancelClearAllKnowledgeBase,
            ),
        userKbUi =
            UserKbPackagesUiState(
                packages = userPackages,
                pendingRemovePackageId = dbUiState.pendingRemovePackageId,
                message = dbUiState.userPackageMessage,
                onImportDirectory = onImportDirectory,
                onUpdateDirectory = { updateDirectoryLauncher.launch(null) },
                onRequestRemove = dbViewModel::requestRemoveUserPackage,
                onConfirmRemove = dbViewModel::confirmRemoveUserPackage,
                onCancelRemove = dbViewModel::cancelRemoveUserPackage,
            ),
        onSelectGroup = { groupKey ->
            if (currentDbQuery.isNotBlank()) onQueryChange("")
            dbViewModel.focusGroupFromDrawer(groupKey)
            localCoroutineScope.launch { dbDrawerState.close() }
        },
        onRetryImport = dbViewModel::retryAssetImport,
        onEdgeAction = {
            localCoroutineScope.launch { dbDrawerState.close() }
        },
    )
}
