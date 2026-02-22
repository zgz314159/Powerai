@file:Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER", "UNUSED")

package com.example.powerai.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PaginationFooter(
    currentPage: Int,
    totalPages: Int? = null,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        OutlinedIconButton(onClick = onPrev, enabled = hasPrev) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一页")
        }

        Surface(
            color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape
        ) {
            Text(
                text = if (totalPages != null && totalPages > 0) "第 $currentPage / $totalPages 页" else "第 $currentPage 页",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge
            )
        }

        OutlinedIconButton(onClick = onNext, enabled = hasNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一页")
        }
    }
}
