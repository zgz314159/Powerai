package com.example.powerai.ui.screen.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Bottom navigation used across `MainScreen`.
 *
 * testTag + combinedClickable on [NavigationBarItem] modifier so UiAutomator
 * `By.res` / `longClick` hit the same node (icon-slot children are merged away).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun MainBottomBar(
    selectedTab: MainBottomTab,
    onTabClick: (MainBottomTab) -> Unit,
    onTabLongClick: (MainBottomTab) -> Unit,
    shouldHide: Boolean,
) {
    if (!shouldHide) {
        NavigationBar(modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            MainBottomTab.values().forEach { tab ->
                val interactionSource = remember(tab) { MutableInteractionSource() }
                NavigationBarItem(
                    selected = selectedTab == tab,
                    // combinedClickable owns press handling (click + long-click / a11y).
                    onClick = {},
                    modifier =
                        Modifier
                            .then(
                                if (tab == MainBottomTab.SMART) {
                                    Modifier.testTag("bottom_tab_smart_icon")
                                } else {
                                    Modifier
                                },
                            )
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onTabClick(tab) },
                                onLongClick = { onTabLongClick(tab) },
                            ),
                    icon = { Icon(tab.icon(), contentDescription = tab.label) },
                    label = { androidx.compose.material3.Text(tab.label) },
                    interactionSource = interactionSource,
                )
            }
        }
    }
}
