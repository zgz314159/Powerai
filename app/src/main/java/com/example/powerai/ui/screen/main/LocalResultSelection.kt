package com.example.powerai.ui.screen.main

import com.example.powerai.core.model.KnowledgeItem

/**
 * Identity used to collapse only genuinely duplicate local results: same payload **and** the same
 * owning KB package. Two packages (e.g. an imported user directory and a bundled asset) that carry
 * identical title/content are kept apart so the user can still open the package they imported;
 * rows without a package id (manual/legacy) collapse by content alone, as before.
 */
internal fun KnowledgeItem.localResultKey(): Pair<String, String?> = content to packageId

/** Local results shown in the list: package-aware de-duplication, capped at [pageSize]. */
internal fun selectLocalDisplayResults(
    references: List<KnowledgeItem>,
    pageSize: Int,
): List<KnowledgeItem> = references.distinctBy { it.localResultKey() }.take(pageSize)

/** Safety filter applied by [LocalResultsPage] before grouping; package-aware, capped at [MAX_SAFE_RESULTS]. */
internal fun applySafeFiltering(results: List<KnowledgeItem>): List<KnowledgeItem> =
    results.distinctBy { it.localResultKey() }.take(MAX_SAFE_RESULTS)

private const val MAX_SAFE_RESULTS = 10
