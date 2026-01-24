package com.example.powerai.ui.screen.main

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.example.powerai.domain.model.KnowledgeItem
import com.example.powerai.ui.component.KnowledgeItemCard
import com.example.powerai.ui.component.SearchBar
import com.example.powerai.ui.screen.hybrid.HybridViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.powerai.ui.jsonrepo.JsonRepositoryScreen

@Composable
fun MainScreen(navController: NavHostController, viewModel: HybridViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var expandedItemId by remember { mutableStateOf<Long?>(null) }
    

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                // Delegate URI import to ViewModel; ViewModel will use injected importer
                viewModel.importDocument(uri)
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                launcher.launch(arrayOf("application/pdf", "text/plain", "application/msword"))
            }) {
                Text("导入文件")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { navController.navigate("jsonrepo") }) {
                Text("打开资源库")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { navController.navigate("settings") }) {
                Text("设置")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SearchBar(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            onSearch = { viewModel.submitQuery(searchQuery) },
            onClear = { searchQuery = "" }
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.references) { item ->
                KnowledgeItemCard(
                    item = item,
                    highlight = searchQuery,
                    expanded = expandedItemId == item.id,
                    onExpand = { expandedItemId = if (expandedItemId == item.id) null else item.id }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "AI回答：${uiState.answer}", modifier = Modifier.padding(top = 8.dp))
    }
}
