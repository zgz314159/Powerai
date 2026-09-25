package com.example.powerai.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 主屏幕顶部应用栏：包括菜单按钮、标题和渐变遮罩背景
 * 
 * @param selectedTab 当前选中的标签页
 * @param onMenuClick 菜单按钮点击回调
 * @param coroutineScope 协程作用域（用于打开/关闭抽屉
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MainTopBar(
    selectedTab: MainBottomTab,
    onMenuClick: suspend () -> Unit,
    coroutineScope: CoroutineScope
) {
    // 获取顶部标题文本
    val topBarTitle = when (selectedTab) {
        MainBottomTab.LOCAL -> "本地"
        MainBottomTab.AI -> "AI"
        MainBottomTab.SMART -> "智能"
        else -> ""  // DATABASE / QUIZ / MINE 不显示共同的顶部
    }

    Box {
        // 渐变遮罩背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White,
                            0.5f to Color.White,
                            0.75f to Color.White.copy(alpha = 0.9f),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )

        // 顶部应用
        CenterAlignedTopAppBar(
            navigationIcon = {
                IconButton(
                    onClick = { coroutineScope.launch { onMenuClick() } },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "对话历史")
                }
            },
            title = {
                Text(text = topBarTitle)
            },
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter),
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}
