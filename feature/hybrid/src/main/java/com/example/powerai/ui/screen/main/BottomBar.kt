package com.example.powerai.ui.screen.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Bottom navigation used across `MainScreen`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainBottomBar(
    selectedTab: MainBottomTab,
    onTabClick: (MainBottomTab) -> Unit,
    onTabLongClick: (MainBottomTab) -> Unit,
    shouldHide: Boolean
) {
    if (!shouldHide) {
        NavigationBar {
            MainBottomTab.values().forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { /* handled by child to allow long-press */ },
                    icon = {
                        val interactionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onTabClick(tab) },
                                    onLongClick = { onTabLongClick(tab) }
                                )
                        ) {
                            Icon(tab.icon(), contentDescription = tab.label)
                        }
                    },
                    label = { androidx.compose.material3.Text(tab.label) }
                )
            }
        }
    }
}
