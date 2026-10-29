package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Smart 页联网开关。文案用「联网 / 仅本地」，避免与提交按钮「搜索」撞名。
 * 「联网」= 允许在时效问题或本地 0 命中时补网页摘要；规程有本地命中时仍只走 KB。
 */
@Composable
internal fun SmartLocalWebSearchToggle(
    webSearchEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row {
        FilterChip(
            selected = webSearchEnabled,
            onClick = { onToggle(true) },
            label = { Text("联网增强") },
            modifier = Modifier.testTag("smart_ds_web_on"),
        )
        Spacer(modifier = Modifier.width(6.dp))
        FilterChip(
            selected = !webSearchEnabled,
            onClick = { onToggle(false) },
            label = { Text("仅本地") },
            modifier = Modifier.testTag("smart_ds_web_off"),
        )
    }
}
