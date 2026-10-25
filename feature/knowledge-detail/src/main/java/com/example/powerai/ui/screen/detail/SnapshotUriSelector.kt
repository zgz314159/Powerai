package com.example.powerai.ui.screen.detail

import com.example.powerai.ui.image.AssetImageUriNormalizer
import com.google.gson.JsonParser

internal object SnapshotUriSelector {

    fun select(
        imageUrisJson: String?,
        pageNumber: Int?,
        blockId: String?,
        isTable: Boolean
    ): String? {
        val uris = parseImageUrisJson(imageUrisJson)
            .mapNotNull { AssetImageUriNormalizer.normalize(it) }
            .filter { it.isNotBlank() }

        if (uris.isEmpty()) return null
        if (uris.size == 1) return uris.first()

        val id = blockId?.trim().orEmpty()
        if (id.isNotBlank()) {
            uris.firstOrNull { it.contains(id, ignoreCase = true) }?.let { return it }
        }

        if (isTable) {
            uris.firstOrNull { it.contains("table", ignoreCase = true) || it.contains("tbl", ignoreCase = true) }?.let { return it }
        }

        val p = pageNumber
        if (p != null && p > 0) {
            // Common naming patterns: ..._p12..., ...page-12..., ...page12...
            val patterns = listOf(
                Regex("(?i)(?:^|[^\\d])p[_-]?0*$p(?:[^\\d]|$)"),
                Regex("(?i)(?:^|[^\\d])page[_-]?0*$p(?:[^\\d]|$)"),
                Regex("(?i)(?:^|[/_\\-])0*$p(?:\\.png|\\.jpg|\\.jpeg|\\.webp|\\.gif|$)")
            )
            for (re in patterns) {
                uris.firstOrNull { re.containsMatchIn(it) }?.let { return it }
            }
        }

        return uris.first()
    }

    private fun parseImageUrisJson(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val root = JsonParser().parse(json)
            if (!root.isJsonArray) return emptyList()
            root.asJsonArray.mapNotNull { el ->
                if (el.isJsonPrimitive && el.asJsonPrimitive.isString) el.asString else null
            }.filter { it.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
