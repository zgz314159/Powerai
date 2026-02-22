package com.example.powerai.ui.screen.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Debug
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.powerai.util.PdfOutlineIndexStore
import com.example.powerai.util.PdfStorage
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    navController: NavHostController,
    fileId: String,
    fileName: String,
    initialHighlightPage: Int? = null,
    initialHighlightBboxJson: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    val pdfFile = remember(fileId, refreshKey) { PdfStorage.getPdfFile(context, fileId) }
    var attemptedAssetRestore by remember(fileId) { mutableStateOf(false) }
    var isRestoringFromAssets by remember(fileId) { mutableStateOf(false) }

    var missingHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileId) {
        // Best-effort: for newly-installed app, original PDFs may still exist in assets.
        // Restore them automatically (no runtime PDF parsing; uses prebuilt index file).
        if (!attemptedAssetRestore) {
            attemptedAssetRestore = true
            if (!pdfFile.exists()) {
                isRestoringFromAssets = true
                val restored = withContext(Dispatchers.IO) {
                    PdfOutlineIndexStore.restorePdfFromAssetsIfPossible(context, fileId)
                }
                isRestoringFromAssets = false
                if (restored) {
                    missingHint = "已从 assets 自动恢复原文 PDF，正在刷新…"
                    refreshKey++
                }
            }
        }
    }
    val relinkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            missingHint = null
            if (uri == null) return@rememberLauncherForActivityResult
            // 重新选择 PDF：校验 sha256 必须与 fileId 一致。
            // 读文件可能较大，放到 IO。
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalArgumentException("无法读取选择的文件")
                    val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    if (!sha.equals(fileId, ignoreCase = true)) {
                        withContext(Dispatchers.Main) {
                            missingHint = "选择的 PDF 与该条目不匹配，请选择原始 PDF（期待 fileId=$fileId，实际=$sha）"
                        }
                        return@launch
                    }
                    PdfStorage.savePdfBytes(context, bytes, fileId)
                    withContext(Dispatchers.Main) {
                        missingHint = "已绑定并保存原文 PDF，正在刷新…"
                        refreshKey++
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        missingHint = "保存失败：${t.message}"
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = if (fileName.isBlank()) "PDF" else fileName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        if (!pdfFile.exists()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isRestoringFromAssets) "正在从 assets 恢复原文 PDF…" else "找不到 PDF 原文件（可能是旧数据未保存原文）")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "fileId=$fileId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { relinkLauncher.launch(arrayOf("application/pdf")) }) {
                        Text("选择 PDF 重新绑定")
                    }
                    missingHint?.let {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            return@Scaffold
        }

        PdfPagesColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            pdfFile = pdfFile,
            highlightPageIndex = initialHighlightPage?.minus(1),
            highlightBox = remember(initialHighlightBboxJson) {
                parsePdfBoundingBoxOrNull(initialHighlightBboxJson)
            }
        )
    }
}

@Composable
private fun PdfPagesColumn(
    modifier: Modifier,
    pdfFile: File,
    highlightPageIndex: Int?,
    highlightBox: PdfBoundingBox?
) {
    var pageCount by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

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
            Text("PDF 无页面")
        }
        return
    }

    val pages = remember(pageCount) { (0 until pageCount).toList() }

    LaunchedEffect(pageCount, highlightPageIndex) {
        val idx = highlightPageIndex
        if (idx != null && idx in 0 until pageCount) {
            // Keep it simple: scroll to the page (no animation) so the highlight is visible.
            runCatching { listState.scrollToItem(idx) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(pages, key = { it }) { index ->
            PdfPageItem(
                pageIndex = index,
                rendererProvider = { rendererHolder.value },
                highlightBox = if (index == highlightPageIndex) highlightBox else null
            )
        }
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    rendererProvider: () -> PdfRenderer?,
    highlightBox: PdfBoundingBox?
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        // Render to (roughly) screen width in px to avoid upscaling blur.
        val targetWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val bitmapState = produceState<Bitmap?>(
            initialValue = null,
            key1 = pageIndex,
            key2 = rendererProvider(),
            key3 = targetWidthPx
        ) {
            value = null
            val renderer = rendererProvider() ?: return@produceState
            val start = System.currentTimeMillis()
            android.util.Log.d(
                "PowerAi.Trace",
                "PdfPageItem render START page=$pageIndex targetW=$targetWidthPx thread=${Thread.currentThread().name} nativeAlloc=${Debug.getNativeHeapAllocatedSize()} freeMem=${Runtime.getRuntime().freeMemory()}"
            )
            value = withContext(Dispatchers.IO) {
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)
                    val pageW = page.width.coerceAtLeast(1)
                    val pageH = page.height.coerceAtLeast(1)

                    // Cap to avoid OOM on very large pages.
                    val maxW = 2400
                    val maxH = 3600

                    val desiredW = maxOf(pageW, targetWidthPx).coerceAtMost(maxW)
                    val scaleW = desiredW.toFloat() / pageW.toFloat()
                    val scaleH = (maxH.toFloat() / pageH.toFloat()).coerceAtMost(scaleW)
                    val scale = minOf(scaleW, scaleH).coerceAtLeast(1f)

                    val outW = (pageW * scale).toInt().coerceAtLeast(1).coerceAtMost(maxW)
                    val outH = (pageH * scale).toInt().coerceAtLeast(1).coerceAtMost(maxH)

                    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)

                    val matrix = android.graphics.Matrix().apply {
                        postScale(scale, scale)
                    }

                    try {
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    } catch (_: Throwable) {
                        try { bitmap.recycle() } catch (_: Throwable) {}
                        null
                    }
                } finally {
                    try { page?.close() } catch (_: Throwable) {}
                }
            }
            android.util.Log.d(
                "PowerAi.Trace",
                "PdfPageItem render DONE page=$pageIndex took=${System.currentTimeMillis() - start}ms thread=${Thread.currentThread().name} bitmap=${value?.width}x${value?.height} nativeAlloc=${Debug.getNativeHeapAllocatedSize()} freeMem=${Runtime.getRuntime().freeMemory()}"
            )
        }

        val bmp = bitmapState.value
        if (bmp == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("第 ${pageIndex + 1} 页渲染失败", color = Color.Gray)
            }
            return@BoxWithConstraints
        }

        // Ensure bitmap is recycled when this composable leaves composition to free native memory.
        DisposableEffect(bmp) {
            onDispose {
                try { bmp.recycle() } catch (_: Throwable) {}
            }
        }

        // The rendered Image is scaled to fill the available width.
        val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val pageHeightPx = (pageWidthPx * bmp.height.toFloat() / bmp.width.toFloat()).coerceAtLeast(1f)
        val pageHeightDp = with(density) { pageHeightPx.toDp() }

        // Compute a focus transform to bring the highlighted bbox into view.
        val focusTransform = remember(highlightBox, bmp.width, bmp.height, pageWidthPx, pageHeightPx) {
            if (highlightBox == null) return@remember null
            val sx = pageWidthPx / bmp.width.toFloat()
            val sy = pageHeightPx / bmp.height.toFloat()
            val left = highlightBox.xMin * sx
            val top = highlightBox.yMin * sy
            val w = (highlightBox.xMax - highlightBox.xMin) * sx
            val h = (highlightBox.yMax - highlightBox.yMin) * sy
            if (w <= 1f || h <= 1f) return@remember null

            val targetFill = 0.88f
            val scaleW = (pageWidthPx * targetFill) / w
            val scaleH = (pageHeightPx * targetFill) / h
            val scale = minOf(scaleW, scaleH).coerceIn(1f, 4f)

            val cx = left + w / 2f
            val cy = top + h / 2f
            var tx = pageWidthPx / 2f - cx * scale
            var ty = pageHeightPx / 2f - cy * scale

            // Clamp translation so the content stays within bounds.
            val minTx = pageWidthPx - pageWidthPx * scale
            val minTy = pageHeightPx - pageHeightPx * scale
            tx = tx.coerceIn(minTx, 0f)
            ty = ty.coerceIn(minTy, 0f)

            FocusTransform(scale = scale, tx = tx, ty = ty)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pageHeightDp)
                .clipToBounds()
        ) {
            // Apply zoom + pan to both the bitmap and the overlay.
            val contentModifier = if (focusTransform != null) {
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = focusTransform.scale
                        scaleY = focusTransform.scale
                        translationX = focusTransform.tx
                        translationY = focusTransform.ty
                    }
            } else {
                Modifier.fillMaxSize()
            }

            Box(modifier = contentModifier) {
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "page_${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                if (highlightBox != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val sx = if (bmp.width > 0) size.width / bmp.width.toFloat() else 1f
                        val sy = if (bmp.height > 0) size.height / bmp.height.toFloat() else 1f
                        val left = highlightBox.xMin * sx
                        val top = highlightBox.yMin * sy
                        val w = (highlightBox.xMax - highlightBox.xMin) * sx
                        val h = (highlightBox.yMax - highlightBox.yMin) * sy
                        if (w > 0f && h > 0f) {
                            drawRect(
                                color = Color(0xFFFFEB3B).copy(alpha = 0.35f),
                                topLeft = Offset(left, top),
                                size = Size(w, h)
                            )
                            drawRect(
                                color = Color(0xFFFFC107).copy(alpha = 0.85f),
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class FocusTransform(
    val scale: Float,
    val tx: Float,
    val ty: Float
)

private data class PdfBoundingBox(
    val xMin: Float,
    val yMin: Float,
    val xMax: Float,
    val yMax: Float
)

private fun parsePdfBoundingBoxOrNull(bboxJson: String?): PdfBoundingBox? {
    if (bboxJson.isNullOrBlank()) return null
    return try {
        val el = JsonParser().parse(bboxJson)
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject

        fun f(key: String): Float? {
            val v = obj.get(key) ?: return null
            if (!v.isJsonPrimitive) return null
            val p = v.asJsonPrimitive
            return when {
                p.isNumber -> p.asFloat
                p.isString -> p.asString.toFloatOrNull()
                else -> null
            }
        }

        val xMin = f("xMin") ?: f("left") ?: return null
        val yMin = f("yMin") ?: f("top") ?: return null
        val xMax = f("xMax") ?: f("right") ?: return null
        val yMax = f("yMax") ?: f("bottom") ?: return null

        if (xMax <= xMin || yMax <= yMin) return null
        PdfBoundingBox(xMin = xMin, yMin = yMin, xMax = xMax, yMax = yMax)
    } catch (_: Throwable) {
        null
    }
}
