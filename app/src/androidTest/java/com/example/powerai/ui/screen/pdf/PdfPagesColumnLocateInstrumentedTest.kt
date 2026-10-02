package com.example.powerai.ui.screen.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PdfPagesColumnLocateInstrumentedTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun createPdf(
        name: String,
        pages: Int,
    ): File {
        val doc = PdfDocument()
        for (index in 0 until pages) {
            val info = PdfDocument.PageInfo.Builder(200, 2800, index + 1).create()
            val page = doc.startPage(info)
            val paint =
                Paint().apply {
                    color = Color.BLACK
                    textSize = 60f
                }
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("page ${index + 1}", 40f, 160f, paint)
            doc.finishPage(page)
        }
        val file = File(context.cacheDir, name)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun awaitContent(state: LazyListState) {
        rule.waitUntil(5_000) { state.layoutInfo.totalItemsCount > 0 }
        rule.waitForIdle()
    }

    @Test
    fun firstLocate_anchorsTargetPage() {
        val pdf = createPdf("locate_first.pdf", pages = 3)
        val state = LazyListState()
        rule.setContent {
            PdfPagesColumn(Modifier.fillMaxSize(), pdf, 1, null, state)
        }
        awaitContent(state)
        assertEquals(1, state.firstVisibleItemIndex)
    }

    @Test
    fun crossPageRelocate_anchorsNewTarget() {
        val pdf = createPdf("locate_cross.pdf", pages = 6)
        val state = LazyListState()
        var target by mutableStateOf(1)
        rule.setContent {
            PdfPagesColumn(Modifier.fillMaxSize(), pdf, target, null, state)
        }
        awaitContent(state)
        assertEquals(1, state.firstVisibleItemIndex)
        rule.runOnIdle { target = 4 }
        rule.waitForIdle()
        assertEquals(4, state.firstVisibleItemIndex)
    }

    @Test
    fun lastPageLocate_anchorsLastPage() {
        val pdf = createPdf("locate_last.pdf", pages = 4)
        val state = LazyListState()
        rule.setContent {
            PdfPagesColumn(Modifier.fillMaxSize(), pdf, 3, null, state)
        }
        awaitContent(state)
        assertEquals(3, state.firstVisibleItemIndex)
    }

    @Test
    fun samePageNewBbox_doesNotMoveList() {
        val pdf = createPdf("locate_same_page.pdf", pages = 5)
        val state = LazyListState()
        var box by mutableStateOf<PdfBoundingBox?>(null)
        rule.setContent {
            PdfPagesColumn(Modifier.fillMaxSize(), pdf, 2, box, state)
        }
        awaitContent(state)
        assertEquals(2, state.firstVisibleItemIndex)
        rule.runOnIdle { box = PdfBoundingBox(10f, 20f, 60f, 80f) }
        rule.waitForIdle()
        assertEquals(2, state.firstVisibleItemIndex)
    }

    @Test
    fun reopenPdf_locatesAgain() {
        val first = createPdf("locate_reopen_a.pdf", pages = 3)
        val second = createPdf("locate_reopen_b.pdf", pages = 5)
        val firstState = LazyListState()
        val secondState = LazyListState()
        var run by mutableIntStateOf(0)
        rule.setContent {
            if (run == 0) {
                PdfPagesColumn(Modifier.fillMaxSize(), first, 1, null, firstState)
            } else {
                PdfPagesColumn(Modifier.fillMaxSize(), second, 3, null, secondState)
            }
        }
        awaitContent(firstState)
        assertEquals(1, firstState.firstVisibleItemIndex)
        rule.runOnIdle { run = 1 }
        awaitContent(secondState)
        assertEquals(3, secondState.firstVisibleItemIndex)
    }

    @Test
    fun manualScrollAfterLocate_isNotPulledBack() {
        val pdf = createPdf("locate_manual.pdf", pages = 8)
        val state = LazyListState()
        var userScrolled by mutableStateOf(false)
        rule.setContent {
            LaunchedEffect(userScrolled) {
                if (userScrolled) {
                    state.scrollToItem(6)
                }
            }
            PdfPagesColumn(Modifier.fillMaxSize(), pdf, 1, null, state)
        }
        awaitContent(state)
        assertEquals(1, state.firstVisibleItemIndex)
        rule.runOnIdle { userScrolled = true }
        rule.waitUntil(5_000) { state.firstVisibleItemIndex == 6 }
        rule.waitForIdle()
        assertEquals(6, state.firstVisibleItemIndex)
    }

    @Test
    fun invalidPageIndex_isSafeNoOp() {
        val pdf = createPdf("locate_invalid.pdf", pages = 3)
        val state = LazyListState()
        rule.setContent {
            PdfPagesColumn(Modifier.fillMaxSize(), pdf, 42, null, state)
        }
        awaitContent(state)
        assertEquals(0, state.firstVisibleItemIndex)
    }
}
