@file:Suppress("DEPRECATION", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DrawerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.chat.ChatSession
import com.example.powerai.domain.model.chat.ChatTurn
import com.example.powerai.ui.component.SearchBar
import kotlinx.coroutines.launch

@Composable
internal fun AiChatScaffold(
    sessions: List<ChatSession>,
    selectedSessionId: Long?,
    currentTurnId: Long?,
    isStreaming: Boolean,
    webSearchEnabled: Boolean,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    onSelectSession: (Long) -> Unit,
    onNewSession: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
    drawerState: DrawerState,
    turnRetryAllowed: Map<Long, Boolean> = emptyMap(),
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var inputFocused by remember { mutableStateOf(false) }
    val inputActive = inputFocused || input.isNotBlank()

    val selectedSessionTurns: List<ChatTurn> = remember(sessions, selectedSessionId) {
        val session = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.firstOrNull()
        session?.turns ?: emptyList()
    }

    LaunchedEffect(selectedSessionId) {
        if (selectedSessionTurns.isNotEmpty()) {
            runCatching { listState.scrollToItem(selectedSessionTurns.size - 1) }
        }
    }

    // When streaming, keep the list scrolled to the last item as its text grows.
    val lastTurn = selectedSessionTurns.lastOrNull()
    val lastTextLen = lastTurn?.answer?.length ?: 0
    // Throttled scrolling: limit animateScrollToItem calls to reduce jumpiness when streaming
    var lastScrollAt by remember { mutableStateOf(0L) }
    LaunchedEffect(lastTextLen, selectedSessionId) {
        if (isStreaming && selectedSessionTurns.isNotEmpty()) {
            try {
                val now = System.currentTimeMillis()
                if (now - lastScrollAt < 120) return@LaunchedEffect
                val lastIndex = selectedSessionTurns.size - 1
                val visibleLast = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                // If not scrolled to bottom, animate to last item; otherwise skip frequent calls
                if (visibleLast < lastIndex) {
                    listState.animateScrollToItem(lastIndex)
                }
                lastScrollAt = System.currentTimeMillis()
            } catch (_: Throwable) {}
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AiHistoryDrawerContent(
                sessions = sessions,
                selectedSessionId = selectedSessionId,
                onNewSession = {
                    onNewSession()
                    coroutineScope.launch { drawerState.close() }
                },
                onSelectSession = { id ->
                    onSelectSession(id)
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Chat list behind; reserve space for the floating input.
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    end = 0.dp,
                    top = 88.dp,
                    bottom = if (inputActive) 120.dp else 100.dp
                )
            ) {
                if (selectedSessionTurns.isEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(40.dp))
                        Text(
                            text = "请输入问题以开始对话",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                itemsIndexed(selectedSessionTurns, key = { _, t -> t.id }) { _, t ->
                    val loadingThis = isStreaming && t.id == currentTurnId
                    TypingAiResponseCard(
                        userMessage = t.question,
                        text = t.answer,
                        isLoading = loadingThis,
                        askedAtMillis = t.askedAtMillis,
                        sources = t.sources,
                        onCopy = { onCopy(t.answer) },
                        onRetry = {
                            onInputChange(t.question)
                            onRetry()
                        },
                        allowRetry = turnRetryAllowed[t.id] ?: false,
                        renderMarkdownWhenPossible = true
                    )
                }
            }

            // 底部输入由外部传入的 showSearchBar 控制
            if (showSearchBar) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = if (inputActive) 10.dp else 6.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                    color = Color.White
                ) {
                    SearchBar(
                        value = input,
                        onValueChange = onInputChange,
                        onSearch = {
                            onSearch()
                            // 回车（搜索）后请求隐藏输入框
                            onShowSearchBarChange(false)
                        },
                        onClear = onClear,
                        label = "",
                        placeholder = "请输入问题",
                        leading = {
                            IconButton(onClick = {
                                onNewSession()
                                onClear()
                            }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "新建会话")
                            }
                        },
                        suffix = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = webSearchEnabled,
                                    onClick = { onWebSearchEnabledChange(true) },
                                    label = { Text("搜索") }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FilterChip(
                                    selected = !webSearchEnabled,
                                    onClick = { onWebSearchEnabledChange(false) },
                                    label = { Text("思考") }
                                )
                            }
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (input.isNotBlank()) {
                                    IconButton(onClick = onClear) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "清空")
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (input.isNotBlank()) {
                                            onSearch()
                                            // 点击发送后也请求隐藏输入框
                                            onShowSearchBarChange(false)
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "发送")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (inputActive) 64.dp else 56.dp)
                            .padding(horizontal = 4.dp, vertical = if (inputActive) 2.dp else 0.dp),
                        onFocusChanged = { focused ->
                            inputFocused = focused
                            onInputFocusChanged(focused)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiHistoryDrawerContent(
    sessions: List<ChatSession>,
    selectedSessionId: Long?,
    onNewSession: () -> Unit,
    onSelectSession: (Long) -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Box(modifier = Modifier.padding(24.dp)) {
            Text(text = "对话历史", style = MaterialTheme.typography.titleLarge)
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = onNewSession) {
                Text(text = "新建会话")
            }
        }

        val drawerItems = remember(sessions) { sessions }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(drawerItems, key = { _, t -> t.id }) { _, t ->
                val isSelected = t.id == selectedSessionId
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = t.title.ifBlank { "新对话" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    selected = isSelected,
                    onClick = { onSelectSession(t.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                )
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }
    }
}

@Composable
internal fun LocalHistoryDrawerContent(
    history: List<com.example.powerai.data.local.LocalSearchEntry>,
    onSelectQuery: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Box(modifier = Modifier.padding(24.dp)) {
            Text(text = "本地搜索历史", style = MaterialTheme.typography.titleLarge)
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = onClearHistory) {
                Text(text = "清除历史")
            }
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无本地搜索历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@ModalDrawerSheet
        }

        // Group by local date (most recent date first)
        val groups = remember(history) {
            history.groupBy { entry ->
                java.time.Instant.ofEpochMilli(entry.timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }.toSortedMap(compareByDescending { it })
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            groups.forEach { (date, entries) ->
                item {
                    val label = runCatching {
                        val today = java.time.LocalDate.now()
                        val yesterday = today.minusDays(1)
                        when (date) {
                            today -> "今天"
                            yesterday -> "昨天"
                            else -> date.toString()
                        }
                    }.getOrDefault(date.toString())
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                itemsIndexed(entries) { _, entry ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = entry.query,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = false,
                        onClick = { onSelectQuery(entry.query) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DatabaseHistoryDrawerContent(
    history: List<com.example.powerai.data.local.LocalSearchEntry>,
    onSelectQuery: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Box(modifier = Modifier.padding(24.dp)) {
            Text(text = "数据库搜索历史", style = MaterialTheme.typography.titleLarge)
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = onClearHistory) {
                Text(text = "清除历史")
            }
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无数据库搜索历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@ModalDrawerSheet
        }

        val groups = remember(history) {
            history.groupBy { entry ->
                java.time.Instant.ofEpochMilli(entry.timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }.toSortedMap(compareByDescending { it })
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            groups.forEach { (date, entries) ->
                item {
                    val label = runCatching {
                        val today = java.time.LocalDate.now()
                        val yesterday = today.minusDays(1)
                        when (date) {
                            today -> "今天"
                            yesterday -> "昨天"
                            else -> date.toString()
                        }
                    }.getOrDefault(date.toString())
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                itemsIndexed(entries) { _, entry ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = entry.query,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = false,
                        onClick = { onSelectQuery(entry.query) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SmartHistoryDrawerContent(
    history: List<com.example.powerai.data.local.LocalSearchEntry>,
    onSelectQuery: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Box(modifier = Modifier.padding(24.dp)) {
            Text(text = "智能搜索历史", style = MaterialTheme.typography.titleLarge)
        }

        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = onClearHistory) {
                Text(text = "清除历史")
            }
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无智能搜索历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@ModalDrawerSheet
        }

        val groups = remember(history) {
            history.groupBy { entry ->
                java.time.Instant.ofEpochMilli(entry.timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }.toSortedMap(compareByDescending { it })
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            groups.forEach { (date, entries) ->
                item {
                    val label = runCatching {
                        val today = java.time.LocalDate.now()
                        val yesterday = today.minusDays(1)
                        when (date) {
                            today -> "今天"
                            yesterday -> "昨天"
                            else -> date.toString()
                        }
                    }.getOrDefault(date.toString())
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                itemsIndexed(entries) { _, entry ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = entry.query,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = false,
                        onClick = { onSelectQuery(entry.query) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    }
}
