package com.example.powerai.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 可复用的搜索输入框组件。
 * 不依赖 ViewModel，接受 value/onValueChange/onSearch 回调以便在不同场景复用。
 */
@Composable
fun SearchBar(
	value: String,
	onValueChange: (String) -> Unit,
	onSearch: () -> Unit,
	onClear: () -> Unit
) {
	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 4.dp),
		label = { Text("搜索知识点") },
		trailingIcon = {
			androidx.compose.foundation.layout.Row {
				if (value.isNotEmpty()) {
					IconButton(onClick = onClear) {
						Icon(imageVector = Icons.Default.Clear, contentDescription = "清空")
					}
				}
				IconButton(onClick = onSearch) {
					Icon(imageVector = Icons.Default.Search, contentDescription = "搜索")
				}
			}
		}
	)
}

