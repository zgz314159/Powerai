package com.example.powerai.ui.screen.mine

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.powerai.ui.screen.settings.SettingsBody
import com.example.powerai.ui.screen.settings.SettingsSection
import com.example.powerai.ui.screen.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<SettingsViewModel>()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? -> uri?.let { viewModel.importUri(it) } }
    )

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("我的") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    launcher.launch(
                        arrayOf(
                            "text/plain",
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/msword",
                            "application/json",
                            "*/*"
                        )
                    )
                }
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("导入文件")
            }
        }
    ) { inner ->
        SettingsBody(
            navController = navController,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(inner),
            topContent = {
                SettingsSection(title = "账号") {
                    Text("登录/注册：未实现")
                }

                Spacer(Modifier.height(8.dp))
            }
        )
    }
}
