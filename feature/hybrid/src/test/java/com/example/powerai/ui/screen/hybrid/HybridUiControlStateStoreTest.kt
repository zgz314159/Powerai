package com.example.powerai.ui.screen.hybrid

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridUiControlStateStoreTest {

    @Test
    fun `restores persisted values from saved state handle`() {
        val handle = SavedStateHandle(
            mapOf(
                "local_current_page" to 3,
                "local_collapsed_group_keys" to arrayListOf("g1", "g2"),
                "local_scroll_index" to 7,
                "local_scroll_offset" to 11,
                "smart_scroll_index" to 5,
                "smart_scroll_offset" to 9
            )
        )

        val store = HybridUiControlStateStore(handle)

        assertEquals(3, store.localCurrentPage.value)
        assertEquals(setOf("g1", "g2"), store.localCollapsedGroupKeys.value)
        assertEquals(7, store.localScrollIndex.value)
        assertEquals(11, store.localScrollOffset.value)
        assertEquals(5, store.smartScrollIndex.value)
        assertEquals(9, store.smartScrollOffset.value)
    }

    @Test
    fun `coerces invalid positions and persists updates`() {
        val handle = SavedStateHandle()
        val store = HybridUiControlStateStore(handle)

        store.setLocalCurrentPage(2)
        store.setLocalScrollPosition(3, 8)
        store.setSmartScrollPosition(2, 6)
        store.setLocalCurrentPage(0)
        store.setLocalScrollPosition(-3, -8)
        store.setSmartScrollPosition(-1, 4)
        store.toggleLocalCollapsedGroupKey("group-a")

        assertEquals(1, store.localCurrentPage.value)
        assertEquals(0, store.localScrollIndex.value)
        assertEquals(0, store.localScrollOffset.value)
        assertEquals(0, store.smartScrollIndex.value)
        assertEquals(4, store.smartScrollOffset.value)
        assertEquals(setOf("group-a"), store.localCollapsedGroupKeys.value)

        assertEquals(1, handle.get<Int>("local_current_page"))
        assertEquals(0, handle.get<Int>("local_scroll_index"))
        assertEquals(0, handle.get<Int>("local_scroll_offset"))
        assertEquals(0, handle.get<Int>("smart_scroll_index"))
        assertEquals(4, handle.get<Int>("smart_scroll_offset"))
        assertEquals(
            setOf("group-a"),
            handle.get<ArrayList<String>>("local_collapsed_group_keys")?.toSet()
        )
    }

    @Test
    fun `reset methods restore default ui control state`() {
        val store = HybridUiControlStateStore(SavedStateHandle())

        store.setLocalCurrentPage(4)
        store.setLocalCollapsedGroupKeys(setOf("group-a"))
        store.setLocalScrollPosition(10, 20)
        store.setSmartScrollPosition(6, 12)

        store.resetLocal()
        store.resetSmart()

        assertEquals(1, store.localCurrentPage.value)
        assertTrue(store.localCollapsedGroupKeys.value.isEmpty())
        assertEquals(0, store.localScrollIndex.value)
        assertEquals(0, store.localScrollOffset.value)
        assertEquals(0, store.smartScrollIndex.value)
        assertEquals(0, store.smartScrollOffset.value)
    }
}