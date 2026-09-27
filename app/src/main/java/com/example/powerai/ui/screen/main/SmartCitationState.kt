package com.example.powerai.ui.screen.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Citation navigation state for [SmartPage]: the tapped citation number,
 * its automatic highlight timeout and the segment index map used to scroll
 * the list to the cited evidence card.
 */
internal class SmartCitationState {
    var selectedNumber by mutableStateOf<Int?>(null)
        internal set
}

@Composable
internal fun rememberSmartCitationState(): SmartCitationState {
    val state = remember { SmartCitationState() }
    LaunchedEffect(state.selectedNumber) {
        if (state.selectedNumber != null) {
            delay(CITATION_HIGHLIGHT_MS)
            state.selectedNumber = null
        }
    }
    return state
}

@Suppress("MagicNumber")
private const val CITATION_HIGHLIGHT_MS = 1600L

internal fun buildCitationTargetIndices(evidenceSegments: List<SmartEvidenceSegment>): Map<Int, Int> {
    if (evidenceSegments.isEmpty()) return emptyMap()

    var itemIndex = 2
    return buildMap {
        evidenceSegments.forEach { segment ->
            itemIndex += 1
            segment.entries.forEach { entry ->
                put(entry.number, itemIndex)
                itemIndex += 1
            }
        }
    }
}
