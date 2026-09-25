package com.example.powerai.ui.screen.hybrid

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class HybridUiControlStateStore(
    private val savedStateHandle: SavedStateHandle
) {
    private companion object {
        const val KEY_LOCAL_CURRENT_PAGE = "local_current_page"
        const val KEY_LOCAL_COLLAPSED_GROUP_KEYS = "local_collapsed_group_keys"
        const val KEY_LOCAL_SCROLL_INDEX = "local_scroll_index"
        const val KEY_LOCAL_SCROLL_OFFSET = "local_scroll_offset"
        const val KEY_SMART_SCROLL_INDEX = "smart_scroll_index"
        const val KEY_SMART_SCROLL_OFFSET = "smart_scroll_offset"
    }

    private val _localCurrentPage = MutableStateFlow(savedStateHandle[KEY_LOCAL_CURRENT_PAGE] ?: 1)
    val localCurrentPage: StateFlow<Int> = _localCurrentPage

    private val _localCollapsedGroupKeys = MutableStateFlow(
        savedStateHandle.get<ArrayList<String>>(KEY_LOCAL_COLLAPSED_GROUP_KEYS)?.toSet().orEmpty()
    )
    val localCollapsedGroupKeys: StateFlow<Set<String>> = _localCollapsedGroupKeys

    private val _localScrollIndex = MutableStateFlow(savedStateHandle[KEY_LOCAL_SCROLL_INDEX] ?: 0)
    val localScrollIndex: StateFlow<Int> = _localScrollIndex

    private val _localScrollOffset = MutableStateFlow(savedStateHandle[KEY_LOCAL_SCROLL_OFFSET] ?: 0)
    val localScrollOffset: StateFlow<Int> = _localScrollOffset

    private val _smartScrollIndex = MutableStateFlow(savedStateHandle[KEY_SMART_SCROLL_INDEX] ?: 0)
    val smartScrollIndex: StateFlow<Int> = _smartScrollIndex

    private val _smartScrollOffset = MutableStateFlow(savedStateHandle[KEY_SMART_SCROLL_OFFSET] ?: 0)
    val smartScrollOffset: StateFlow<Int> = _smartScrollOffset

    fun setLocalCurrentPage(page: Int) {
        val resolved = page.coerceAtLeast(1)
        if (_localCurrentPage.value == resolved) return
        _localCurrentPage.value = resolved
        savedStateHandle[KEY_LOCAL_CURRENT_PAGE] = resolved
    }

    fun setLocalCollapsedGroupKeys(keys: Set<String>) {
        if (_localCollapsedGroupKeys.value == keys) return
        _localCollapsedGroupKeys.value = keys
        savedStateHandle[KEY_LOCAL_COLLAPSED_GROUP_KEYS] = ArrayList(keys)
    }

    fun toggleLocalCollapsedGroupKey(key: String) {
        val updated = if (_localCollapsedGroupKeys.value.contains(key)) {
            _localCollapsedGroupKeys.value - key
        } else {
            _localCollapsedGroupKeys.value + key
        }
        setLocalCollapsedGroupKeys(updated)
    }

    fun setLocalScrollPosition(index: Int, offset: Int) {
        val resolvedIndex = index.coerceAtLeast(0)
        val resolvedOffset = offset.coerceAtLeast(0)
        if (_localScrollIndex.value == resolvedIndex && _localScrollOffset.value == resolvedOffset) return
        _localScrollIndex.value = resolvedIndex
        _localScrollOffset.value = resolvedOffset
        savedStateHandle[KEY_LOCAL_SCROLL_INDEX] = resolvedIndex
        savedStateHandle[KEY_LOCAL_SCROLL_OFFSET] = resolvedOffset
    }

    fun setSmartScrollPosition(index: Int, offset: Int) {
        val resolvedIndex = index.coerceAtLeast(0)
        val resolvedOffset = offset.coerceAtLeast(0)
        if (_smartScrollIndex.value == resolvedIndex && _smartScrollOffset.value == resolvedOffset) return
        _smartScrollIndex.value = resolvedIndex
        _smartScrollOffset.value = resolvedOffset
        savedStateHandle[KEY_SMART_SCROLL_INDEX] = resolvedIndex
        savedStateHandle[KEY_SMART_SCROLL_OFFSET] = resolvedOffset
    }

    fun resetLocal() {
        setLocalCurrentPage(1)
        setLocalCollapsedGroupKeys(emptySet())
        setLocalScrollPosition(0, 0)
    }

    fun resetSmart() {
        setSmartScrollPosition(0, 0)
    }
}
