package com.example.powerai.ui.jsonrepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.powerai.data.json.JsonEntry

@Composable
fun JsonContentArea(
    isWide: Boolean,
    showEntries: Boolean,
    displayed: List<JsonEntry>,
    onUpdateEntry: (JsonEntry) -> Unit
) {
    if (!showEntries) {
        EmptyState("请选择文件以查看数据")
    } else {
        if (isWide) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(320.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayed) { entry ->
                    EntryRow(entry) { updated -> onUpdateEntry(updated) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayed) { entry ->
                    EntryRow(entry) { updated -> onUpdateEntry(updated) }
                }
            }
        }
    }
}
