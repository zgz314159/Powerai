package com.example.powerai.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.powerai.ui.settings.SettingsViewModel
import com.example.powerai.ui.components.SearchBar
import com.example.powerai.ui.components.KnowledgeEntryCard
import com.example.powerai.data.knowledge.KnowledgeRepository
import androidx.navigation.NavHostController
import com.example.powerai.navigation.Screen
// removed duplicate/unused imports
import com.example.powerai.domain.model.KnowledgeItem
// MaterialTheme already imported above
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, viewModel: KnowledgeViewModel, settingsViewModel: SettingsViewModel, fontScaleParam: Float? = null) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.searchResults.collectAsState()
    val fontScale by settingsViewModel.fontSize.collectAsState()
    val fontScaleEffective = fontScaleParam ?: fontScale

    var editingEntry by remember { mutableStateOf<KnowledgeRepository.KnowledgeEntry?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "电力线路工知识库", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(12.dp)
        ) {
            SearchBar(query = query, onQueryChange = { query = it }, onSearch = { viewModel.search(it) })

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { viewModel.prevPage() }) { Text("上一页") }
                Button(onClick = { viewModel.nextPage() }) { Text("下一页") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = results) { item: KnowledgeRepository.KnowledgeEntry ->
                    KnowledgeEntryCard(entry = item, highlight = query, fontScale = fontScaleEffective, onEdit = {
                        editingEntry = it
                        editTitle = it.title
                        editContent = it.content
                    })
                }
            }

            // Edit dialog
            editingEntry?.let { e ->
                AlertDialog(
                    onDismissRequest = { editingEntry = null },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.editEntryFull(null, e.id, editTitle, editContent)
                            editingEntry = null
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { editingEntry = null }) { Text("Cancel") } },
                    title = { Text("Edit Entry") },
                    text = {
                        Column {
                            OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Title") })
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = editContent, onValueChange = { editContent = it }, label = { Text("Content") }, maxLines = 8)
                        }
                    }
                )
            }
        }
    }
}

