package com.example.powerai.ui.screen.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfLocateIndexTest {
    @Test
    fun validIndexIsResolved() {
        assertEquals(0, resolvePdfLocateIndex(0, 3))
        assertEquals(2, resolvePdfLocateIndex(2, 3))
    }

    @Test
    fun missingPageIsIgnored() {
        assertNull(resolvePdfLocateIndex(null, 3))
    }

    @Test
    fun outOfRangePageIsIgnored() {
        assertNull(resolvePdfLocateIndex(-1, 3))
        assertNull(resolvePdfLocateIndex(3, 3))
    }

    @Test
    fun emptyDocumentIsIgnored() {
        assertNull(resolvePdfLocateIndex(0, 0))
    }
}
