package com.example.powerai.ui.screen.pdf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PdfPageItem(
    pageIndex: Int,
    rendererProvider: () -> PdfRenderer?,
    highlightBox: PdfBoundingBox?
) {
    val density = LocalDensity.current
    val renderer = rendererProvider()

    // Store original page dimensions in points
    var originalPageSize by remember(pageIndex, renderer) {
        mutableStateOf<Size?>(null)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        val targetWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val bitmapState = produceState<Bitmap?>(
            initialValue = null,
            key1 = pageIndex,
            key2 = renderer,
            key3 = targetWidthPx
        ) {
            value = null
            if (renderer == null) return@produceState
            value = withContext(Dispatchers.IO) {
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(pageIndex)
                    val pageW = page.width.coerceAtLeast(1)
                    val pageH = page.height.coerceAtLeast(1)
                    originalPageSize = Size(pageW.toFloat(), pageH.toFloat())

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
                        try {
                            bitmap.recycle()
                        } catch (_: Throwable) {
                        }
                        null
                    }
                } finally {
                    try {
                        page?.close()
                    } catch (_: Throwable) {
                    }
                }
            }
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

        DisposableEffect(bmp) {
            onDispose {
                try {
                    bmp.recycle()
                } catch (_: Throwable) {
                }
            }
        }

        val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val pageHeightPx = (pageWidthPx * bmp.height.toFloat() / bmp.width.toFloat()).coerceAtLeast(1f)
        val pageHeightDp = with(density) { pageHeightPx.toDp() }

        val focusTransform = remember(highlightBox, originalPageSize, pageWidthPx, pageHeightPx) {
            calculateFocusTransform(highlightBox, originalPageSize, pageWidthPx, pageHeightPx)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pageHeightDp)
                .clipToBounds()
        ) {
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
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "page_${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                HighlightOverlay(highlightBox, originalPageSize)
            }
        }
    }
}

private fun calculateFocusTransform(
    highlightBox: PdfBoundingBox?,
    originalPageSize: Size?,
    pageWidthPx: Float,
    pageHeightPx: Float
): FocusTransform? {
    if (highlightBox == null || originalPageSize == null) return null

    // Scale from points to displayed pixels
    val sx = pageWidthPx / originalPageSize.width
    val sy = pageHeightPx / originalPageSize.height

    val left = highlightBox.xMin * sx
    val top = highlightBox.yMin * sy
    val w = (highlightBox.xMax - highlightBox.xMin) * sx
    val h = (highlightBox.yMax - highlightBox.yMin) * sy

    if (w <= 1f || h <= 1f) return null

    val targetFill = 0.88f
    val scaleW = (pageWidthPx * targetFill) / w
    val scaleH = (pageHeightPx * targetFill) / h
    val scale = minOf(scaleW, scaleH).coerceIn(1f, 4f)

    val cx = left + w / 2f
    val cy = top + h / 2f
    var tx = pageWidthPx / 2f - cx * scale
    var ty = pageHeightPx / 2f - cy * scale

    val minTx = pageWidthPx - pageWidthPx * scale
    val minTy = pageHeightPx - pageHeightPx * scale
    tx = tx.coerceIn(minTx, 0f)
    ty = ty.coerceIn(minTy, 0f)

    return FocusTransform(scale = scale, tx = tx, ty = ty)
}

@Composable
private fun HighlightOverlay(highlightBox: PdfBoundingBox?, originalPageSize: Size?) {
    if (highlightBox == null || originalPageSize == null) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Scale from points to displayed pixels (size.width/height is the Canvas size)
        val sx = size.width / originalPageSize.width
        val sy = size.height / originalPageSize.height

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

private data class FocusTransform(
    val scale: Float,
    val tx: Float,
    val ty: Float
)
