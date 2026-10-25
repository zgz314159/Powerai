package com.example.powerai.ui.screen.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotUriSelectorTest {

    @Test
    fun select_prefersBlockIdMatch() {
        val json = """["file:///android_asset/images/p1.png","file:///android_asset/images/block_abc.png"]"""
        val uri = SnapshotUriSelector.select(
            imageUrisJson = json,
            pageNumber = 1,
            blockId = "abc",
            isTable = false
        )
        assertEquals("file:///android_asset/images/block_abc.png", uri)
    }

    @Test
    fun select_prefersPageMatchWhenNoBlockId() {
        val json = """["file:///android_asset/images/page_2.png","file:///android_asset/images/page_10.png"]"""
        val uri = SnapshotUriSelector.select(
            imageUrisJson = json,
            pageNumber = 10,
            blockId = null,
            isTable = false
        )
        assertEquals("file:///android_asset/images/page_10.png", uri)
    }
}
