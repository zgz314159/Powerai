package com.example.powerai.domain.model

/**
 * Data for navigating between entries or hits in detail screen.
 */
data class DetailEntryNavigation(
    val mode: DetailEntryNavigationMode,
    val currentIndex: Int,
    val totalCount: Int,
    val previousItemId: Long?,
    val nextItemId: Long?
)

enum class DetailEntryNavigationMode {
    HIT,
    ENTRY
}
