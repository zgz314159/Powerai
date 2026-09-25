package com.example.powerai.data.importer

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal object KbAssetPathNormalizer {
    private const val ANDROID_ASSET_PREFIX = "file:///android_asset/"

    fun resolveEntitySource(rawSource: String?, assetJsonPath: String?): String? {
        val context = AssetContext.fromAssetJsonPath(assetJsonPath)
        return if (context != null) {
            val path = context.assetDirPath
            if (path.startsWith("assets/")) path else "assets/$path"
        } else {
            rawSource?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    fun sourcePrefixForAsset(assetJsonPath: String): String? {
        return resolveEntitySource(rawSource = null, assetJsonPath = assetJsonPath)
    }

    fun normalizeBlocksJson(rawBlocksJson: String?, assetJsonPath: String?): String? {
        val blocks = rawBlocksJson?.takeIf { it.isNotBlank() } ?: return rawBlocksJson
        val context = AssetContext.fromAssetJsonPath(assetJsonPath)
        return try {
            val root = JsonParser().parse(blocks)
            var changed = false
            if (context != null) {
                if (normalizeElement(root, context)) changed = true
            }
            if (normalizeFieldAliases(root)) changed = true
            if (changed) root.toString() else rawBlocksJson
        } catch (_: Throwable) {
            rawBlocksJson
        }
    }

    private fun normalizeFieldAliases(element: JsonElement): Boolean {
        return when {
            element.isJsonArray -> {
                var changed = false
                element.asJsonArray.forEach { if (normalizeFieldAliases(it)) changed = true }
                changed
            }
            element.isJsonObject -> {
                var changed = false
                val obj = element.asJsonObject

                // bbox -> boundingBox
                if (obj.has("bbox") && !obj.has("boundingBox")) {
                    obj.add("boundingBox", obj.get("bbox"))
                    changed = true
                }

                // table_rows -> rows
                if (obj.has("table_rows") && !obj.has("rows")) {
                    obj.add("rows", obj.get("table_rows"))
                    changed = true
                }

                // table_cells -> cells
                if (obj.has("table_cells") && !obj.has("cells")) {
                    obj.add("cells", obj.get("table_cells"))
                    changed = true
                }

                // Recursively normalize children
                obj.entrySet().forEach { if (normalizeFieldAliases(it.value)) changed = true }
                changed
            }
            else -> false
        }
    }

    fun normalizeMarkdownImageUris(markdown: String, assetJsonPath: String?): String {
        val context = AssetContext.fromAssetJsonPath(assetJsonPath) ?: return markdown
        val imageRegex = Regex("!\\[[^]]*\\]\\(([^)]+)\\)")
        return imageRegex.replace(markdown) { match ->
            val rawUri = match.groupValues.getOrNull(1).orEmpty()
            val normalized = normalizeImageUri(rawUri, context)
            if (normalized == rawUri) match.value else match.value.replace(rawUri, normalized)
        }
    }

    private fun normalizeElement(element: JsonElement, context: AssetContext): Boolean {
        return when {
            element.isJsonArray -> normalizeArray(element.asJsonArray, context)
            element.isJsonObject -> normalizeObject(element.asJsonObject, context)
            else -> false
        }
    }

    private fun normalizeArray(array: JsonArray, context: AssetContext): Boolean {
        var changed = false
        for (index in 0 until array.size()) {
            val child = array[index]
            if (normalizeElement(child, context)) changed = true
        }
        return changed
    }

    private fun normalizeObject(obj: JsonObject, context: AssetContext): Boolean {
        var changed = false
        val imageKeys = setOf("src", "url", "imageSrc", "imageUri", "image_uri", "snapshotUri", "snapshot_uri")
        val entries = obj.entrySet().toList()
        for ((key, value) in entries) {
            if (key in imageKeys && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                val raw = value.asString
                val normalized = normalizeImageUri(raw, context)
                if (normalized != raw) {
                    obj.addProperty(key, normalized)
                    changed = true
                }
                continue
            }
            if (normalizeElement(value, context)) changed = true
        }
        return changed
    }

    private fun normalizeImageUri(raw: String, context: AssetContext): String {
        var candidate = raw.trim().replace('\\', '/')
        if (candidate.isBlank()) return raw
        if (candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true) ||
            candidate.startsWith("content://", ignoreCase = true)
        ) {
            return raw
        }

        // Fix for missing folder in "专业知识" category
        val leaf = context.assetDirName
        if (leaf.isNotBlank() && !candidate.contains(leaf)) {
            if (candidate.contains("专业知识//截图/")) {
                candidate = candidate.replace("专业知识//截图/", "专业知识/$leaf/截图/")
            } else if (candidate.contains("专业知识/截图/")) {
                candidate = candidate.replace("专业知识/截图/", "专业知识/$leaf/截图/")
            }
        }

        // Fold multiple slashes but keep file:///
        if (candidate.contains("//") && !candidate.startsWith("file:///")) {
            candidate = candidate.replace(Regex("/{2,}"), "/")
        } else if (candidate.startsWith("file:///")) {
            val pathPart = candidate.removePrefix("file:///")
            candidate = "file:///" + pathPart.replace(Regex("/{2,}"), "/")
        }

        val relativePath = when {
            candidate.startsWith(ANDROID_ASSET_PREFIX, ignoreCase = true) ->
                normalizeAssetRelativePath(candidate.removePrefix(ANDROID_ASSET_PREFIX), context)
            candidate.startsWith("/android_asset/", ignoreCase = true) ->
                normalizeAssetRelativePath(candidate.removePrefix("/android_asset/"), context)
            candidate.startsWith("assets/", ignoreCase = true) ->
                normalizeAssetRelativePath(candidate.removePrefix("assets/"), context)
            candidate.startsWith("/assets/", ignoreCase = true) ->
                normalizeAssetRelativePath(candidate.removePrefix("/assets/"), context)
            candidate.startsWith("kb/", ignoreCase = true) -> normalizeAssetRelativePath(candidate, context)
            candidate.startsWith("/kb/", ignoreCase = true) ->
                normalizeAssetRelativePath(candidate.removePrefix("/"), context)
            candidate.startsWith("${context.assetDirName}/") -> normalizeAssetRelativePath(candidate, context)
            candidate.startsWith("截图/") -> normalizeAssetRelativePath(candidate, context)
            looksLikeImageFile(candidate) -> "${context.assetDirPath}/$candidate"
            else -> null
        }

        return relativePath?.let { "$ANDROID_ASSET_PREFIX$it" } ?: raw
    }

    private fun normalizeAssetRelativePath(rawPath: String, context: AssetContext): String? {
        val normalized = rawPath.trim().replace('\\', '/').trim('/').removePrefix("./")
        if (normalized.isBlank()) return null
        if (normalized == context.assetDirPath || normalized.startsWith("${context.assetDirPath}/")) {
            return normalized
        }
        if (normalized.startsWith("kb/")) {
            val suffix = normalized.removePrefix("kb/")
            if (suffix.startsWith("${context.assetDirName}/")) {
                return "${context.assetDirPath}/${suffix.removePrefix("${context.assetDirName}/")}"
            }
            return normalized
        }
        if (normalized.startsWith("${context.assetDirName}/")) {
            return "${context.assetDirPath}/${normalized.removePrefix("${context.assetDirName}/")}"
        }
        if (normalized.startsWith("截图/")) {
            return "${context.assetDirPath}/$normalized"
        }
        return null
    }

    private fun looksLikeImageFile(value: String): Boolean {
        val lower = value.lowercase()
        return lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".gif")
    }

    private data class AssetContext(
        val assetDirPath: String,
        val assetDirName: String
    ) {
        companion object {
            fun fromAssetJsonPath(assetJsonPath: String?): AssetContext? {
                val normalized = assetJsonPath
                    ?.trim()
                    ?.replace('\\', '/')
                    ?.trim('/')
                    ?.takeIf { it.isNotBlank() }
                    ?: return null
                val assetDir = normalized.substringBeforeLast('/', normalized).trim('/').takeIf { it.isNotBlank() }
                    ?: return null
                val assetDirName = assetDir.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
                return AssetContext(assetDirPath = assetDir, assetDirName = assetDirName)
            }
        }
    }
}
