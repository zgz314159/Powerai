package com.example.powerai.ui.screen.hybrid

import kotlin.math.max
import kotlin.math.min

internal data class HybridManualPageSlice<T>(
    val page: Int,
    val totalPages: Int,
    val items: List<T>,
    val hasPrev: Boolean,
    val hasNext: Boolean
)

internal object HybridManualPaging {

    fun <T> slice(
        allItems: List<T>,
        page: Int,
        pageSize: Int
    ): HybridManualPageSlice<T> {
        require(pageSize > 0) { "pageSize must be > 0" }

        if (allItems.isEmpty()) {
            return HybridManualPageSlice(
                page = 1,
                totalPages = 0,
                items = emptyList(),
                hasPrev = false,
                hasNext = false
            )
        }

        val total = allItems.size
        val totalPages = (total + pageSize - 1) / pageSize
        val coercedPage = page.coerceIn(1, max(1, totalPages))

        val start = (coercedPage - 1) * pageSize
        val end = min(start + pageSize, total)

        val pageItems = if (start < end) allItems.subList(start, end) else emptyList()
        return HybridManualPageSlice(
            page = coercedPage,
            totalPages = totalPages,
            items = pageItems,
            hasPrev = coercedPage > 1,
            hasNext = coercedPage < totalPages
        )
    }
}
