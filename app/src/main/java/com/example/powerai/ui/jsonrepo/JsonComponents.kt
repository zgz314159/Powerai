package com.example.powerai.ui.jsonrepo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.powerai.data.json.JsonKnowledgeFile

@Composable
fun ExportFabGroup(
    expanded: Boolean,
    onToggle: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = onExportCsv,
                    icon = { Text("CSV") },
                    text = { Text("导出 CSV") },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ExtendedFloatingActionButton(
                    onClick = onExportJson,
                    icon = { Text("JSON") },
                    text = { Text("导出 JSON") },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
        FloatingActionButton(onClick = onToggle, containerColor = MaterialTheme.colorScheme.primary) {
            val icon = if (expanded) Icons.Default.Close else Icons.Default.Inbox
            Icon(icon, contentDescription = null, modifier = Modifier.rotate(rotation))
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Text("📥", style = MaterialTheme.typography.titleLarge)
            }
            Text(text, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun JsonDrawerContent(
    files: List<JsonKnowledgeFile>,
    selectedFileId: String?,
    onFileSelected: (JsonKnowledgeFile) -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Box(Modifier.padding(24.dp)) {
            Text("知识库文件", style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(files) { file ->
                val isSelected = file.fileId == selectedFileId
                NavigationDrawerItem(
                    icon = {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(36.dp)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(file.fileName.take(1).uppercase(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    label = { Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = isSelected,
                    onClick = { onFileSelected(file) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(NavigationDrawerItemDefaults.ItemPadding)
                        .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) else Modifier)
                )
            }
        }
    }
}
