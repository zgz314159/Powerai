package com.example.powerai.ui.screen.pdf

import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch
import java.io.File
import android.os.ParcelFileDescriptor

@Composable
internal fun PdfPagesColumn(
    modifier: Modifier,
    pdfFile: File,
    highlightPageIndex: Int?,
    highlightBox: PdfBoundingBox?,
    listState: LazyListState = rememberLazyListState(),
) {
    var pageCount by remember { mutableIntStateOf(0) }

    // Open renderer once for this file.
    val rendererHolder = remember(pdfFile.absolutePath) {
        mutableStateOf<PdfRenderer?>(null)
    }

    DisposableEffect(pdfFile.absolutePath) {
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        rendererHolder.value = renderer
        pageCount = renderer.pageCount

        onDispose {
            try { renderer.close() } catch (_: Throwable) {}
            try { pfd.close() } catch (_: Throwable) {}
            rendererHolder.value = null
        }
    }

    if (pageCount <= 0) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("PDF 无页")
        }
        return
    }

    val pages = remember(pageCount) { (0 until pageCount).toList() }

    LaunchedEffect(pageCount, highlightPageIndex) {
        val idx = resolvePdfLocateIndex(highlightPageIndex, pageCount) ?: return@LaunchedEffect
        listState.scrollToItem(idx)
    }

    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        // Page items keep a short placeholder height until their bitmap is
        // rendered, so the list can be too small to scroll the target page to
        // the top when the locate request arrives. A trailing spacer keeps the
        // target page reachable; the list anchors on it while the real page
        // heights settle.
        val trailingSpace = maxHeight
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pages, key = { it }) { index ->
                PdfPageItem(
                    pageIndex = index,
                    rendererProvider = { rendererHolder.value },
                    highlightBox = if (index == highlightPageIndex) highlightBox else null,
                )
            }
            item(key = "trailing-locate-space") {
                Spacer(modifier = Modifier.height(trailingSpace))
            }
        }
    }
}

/**
 * Resolves a zero-based highlight page index against the current page count.
 * Returns null (safe no-op) for a missing or out-of-range page.
 */
internal fun resolvePdfLocateIndex(
    highlightPageIndex: Int?,
    pageCount: Int,
): Int? {
    if (highlightPageIndex == null || pageCount <= 0) return null
    return highlightPageIndex.takeIf { it in 0 until pageCount }
}
