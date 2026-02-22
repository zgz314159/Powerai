@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.ui.component.SearchBar

// ViewModel is supplied by the caller (avoid default composable viewModel())
/**
 * 简单的搜索页面，将 UI 状态从 `SearchViewModel` 中读取并以 Compose 展示。
 * 该层不包含业务逻辑，所有动作都委托给 ViewModel。
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SearchBar(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChanged(it) },
                onSearch = { viewModel.onSearch() },
                onClear = { viewModel.onQueryChanged("") }
            )

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            } else {
                if (uiState.results.isEmpty()) {
                    Text(
                        text = "无匹配结果",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(top = 12.dp)) {
                        items(uiState.results) { item ->
                            Text(
                                text = item.title,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            uiState.error?.let { err ->
                Text(text = err, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
