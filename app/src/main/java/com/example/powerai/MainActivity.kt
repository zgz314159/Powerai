package com.example.powerai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.powerai.ui.screen.hybrid.HybridViewModel
import com.example.powerai.ui.screen.main.DisplayMode
import com.example.powerai.ui.theme.PowerAiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppEntry()
        }
    }
}

@Composable
fun AppEntry() {
    val dark = isSystemInDarkTheme()
    PowerAiTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            val context = LocalContext.current
            val navController = rememberNavController()
            // auto-run query injection for diagnostics
            val hybridViewModel: com.example.powerai.ui.screen.hybrid.HybridViewModel = hiltViewModel()
            LaunchedEffect(key1 = context) {
                val act = context as? android.app.Activity
                val raw = act?.intent?.getStringExtra("auto_query")
                val q = raw?.let {
                    try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Throwable) { it }
                }
                if (!q.isNullOrBlank()) {
                    hybridViewModel.submitQuery(q, DisplayMode.LOCAL)
                }
            }
            com.example.powerai.ui.AppNavHost(navController)
        }
    }
}

/**
 * AppUi
 *
 * A thin, view-model-free navigation host for the app UI. This composable only
 * declares the `NavHost` and accepts content lambdas for each route. Callers
 * (for example `AppEntry`) are responsible for creating/providing ViewModels
 * and wiring them into the `homeContent` / `settingsContent` / `jsonRepoContent`
 * lambdas.
 *
 * Rationale: keeping `AppUi` free of Hilt/ViewModel calls makes it safe to
 * reuse in `@Preview` and other non-runtime contexts where Hilt/SavedState
 * owners are not available. For previews, use the `SafePreview` helper
 * (see `ui.preview.PreviewTemplates`) to render static UI samples without
 * triggering `hiltViewModel()`.
 */

@Preview(showBackground = true)
@Composable
fun AppEntryPreview() {
    val dark = false
    PowerAiTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = androidx.compose.ui.Modifier.padding(16.dp)) {
                Text("PowerAi Preview", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                Text("Preview UI without ViewModel injection")
            }
        }
    }
}
