package com.example.powerai.core.data.importer

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

/**
 * Rewrites image-bearing keys inside a KB entry (or its `blocks` payload) in place. User-directory
 * imports use it to turn relative shot paths into stable app-private URIs before the entry is
 * mapped, so the shared [JsonEntryMapper] stays unaware of any resolver.
 */
internal object JsonImageRefRewriter {
    private val IMAGE_REF_KEYS =
        setOf("src", "url", "imageSrc", "imageUri", "image_uri", "snapshotUri", "snapshot_uri")

    /**
     * Rewrites every image reference (including `images[]`) under [element]; returns true when
     * anything changed. A [rewriter] exception propagates, so a malformed or missing asset fails
     * the import instead of being silently dropped.
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    fun rewrite(
        element: JsonElement,
        rewriter: (String) -> String,
    ): Boolean {
        var changed = false
        when {
            element.isJsonArray -> element.asJsonArray.forEach { if (rewrite(it, rewriter)) changed = true }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                for ((key, value) in obj.entrySet().toList()) {
                    when {
                        key in IMAGE_REF_KEYS && value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
                            val raw = value.asString
                            if (raw.isNotBlank()) {
                                val rewritten = rewriter(raw)
                                if (rewritten != raw) {
                                    obj.addProperty(key, rewritten)
                                    changed = true
                                }
                            }
                        }
                        key == "images" && value.isJsonArray -> {
                            val array = value.asJsonArray
                            for (index in 0 until array.size()) {
                                val item = array[index]
                                if (item.isJsonPrimitive && item.asJsonPrimitive.isString && item.asString.isNotBlank()) {
                                    val rewritten = rewriter(item.asString)
                                    if (rewritten != item.asString) {
                                        array.set(index, JsonPrimitive(rewritten))
                                        changed = true
                                    }
                                }
                            }
                        }
                        else -> if (rewrite(value, rewriter)) changed = true
                    }
                }
            }
        }
        return changed
    }
}
