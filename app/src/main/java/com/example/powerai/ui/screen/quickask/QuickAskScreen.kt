@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.quickask

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.ui.component.KnowledgeItemCard
import com.example.powerai.ui.component.SearchBar

@Composable
@Suppress("UNUSED_PARAMETER")
fun QuickAskScreen(
    categories: List<String> = listOf("安规", "架空线路", "电缆", "带电作业"),
    items: List<KnowledgeItem>,
    _onCategorySelected: (String) -> Unit,
    _selectedCategory: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.width(120.dp).padding(8.dp)) {
                categories.forEach { category ->
                    Text(
                        text = category,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            Column(modifier = Modifier.fillMaxHeight().padding(8.dp)) {
                SearchBar(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    onSearch = {},
                    onClear = { onSearchQueryChange("") }
                )
                LazyColumn {
                    items(items) { item ->
                        var expanded by remember { mutableStateOf(false) }
                        KnowledgeItemCard(item = item, highlight = searchQuery)
                        if (expanded) {
                            Text(text = item.content)
                        }
                    }
                }
            }
        }
    }
}
