package com.example.powerai.ui.screen.main

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.powerai.ui.screen.mine.MineScreen
import com.example.powerai.ui.test.EmbeddingTestActivity

/**
 * Quiz tab placeholder - stub implementation.
 */
@Composable
internal fun QuizTabContent(
    innerPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(),
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "答题功能未实")
    }
}

/**
 * Mine tab content - displays user profile/settings.
 */
@Composable
internal fun MineTabContent(
    navController: NavHostController,
    innerPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(),
) {
    val context = LocalContext.current
    MineScreen(
        navController = navController,
        showTopBar = false,
        innerPadding = innerPadding,
        onDevBackdoor = {
            try {
                context.startActivity(
                    Intent(context, EmbeddingTestActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Throwable) {
            }
        },
    )
}
