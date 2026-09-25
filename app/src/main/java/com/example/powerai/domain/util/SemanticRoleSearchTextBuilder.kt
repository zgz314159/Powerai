package com.example.powerai.domain.util

import com.example.powerai.core.model.util.TextSanitizer

import com.example.powerai.core.model.util.BlocksJsonUtils
import com.example.powerai.core.model.util.BlocksJsonUtils.asJsonObjectOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.stringOrNull
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonArray

internal object SemanticRoleSearchTextBuilder {
    data class SemanticRoleProfile(
        val totalBlocks: Int = 0,
        val headingCount: Int = 0,
        val bodyCount: Int = 0,
        val tableCount: Int = 0,
        val tableNoteCount: Int = 0,
        val captionCount: Int = 0,
        val artifactCount: Int = 0,
        val figureCount: Int = 0,
        val figureNodeCount: Int = 0,
        val dominantRole: String = "",
        val lowValueOnly: Boolean = false,
        val figureLabelMatchCount: Int = 0,
        val figureCaptionMatchCount: Int = 0,
        val matchedFigureLabel: String = "",
        val matchedFigureCaption: String = "",
        val matchedCanonicalFigureNodeId: String = ""
    )

    private val roleWeights = linkedMapOf(
        "heading" to 3,
        "body" to 2,
        "table" to 2,
        "figure_node" to 1,
        "table_note" to 1,
        "caption" to 1,
        "artifact" to 0,
        "figure" to 0
    )

    private val contentKeys = listOf("code", "markdown", "text", "title", "content", "caption", "alt", "label")
    private val arrayKeys = listOf("cells", "rows", "spans")
    private val metaKeys = setOf(
        "id",
        "blockid",
        "type",
        "blocktype",
        "language",
        "pagenumber",
        "position",
        "kind",
        "contextlabel",
        "semanticrole",
        "bbox",
        "boundbox",
        "boundingbox",
        "readorder",
        "readingoOrder",
        "structuresource",
        "src",
        "imageuri",
        "image_uri",
        "imageuris",
        "snapshoturi",
        "snapshot_uri",
        "labelnormalized",
        "captiontexts",
        "captionblockids",
        "imageblockids",
        "pagenumbers",
        "continued"
    )

    fun buildSearchPayload(
        title: String,
        source: String,
        category: String,
        pageNumber: Int?,
        keywords: List<String>,
        plainText: String,
        blocksJson: String?
    ): String {
        val weightedPlainText = buildWeightedPlainText(blocksJson, plainText)
        return ContextualSearchTextBuilder.buildSearchPayload(
            title = title,
            source = source,
            category = category,
            pageNumber = pageNumber,
            keywords = keywords,
            plainText = weightedPlainText
        )
    }

    fun buildNormalizedSearchContent(
        title: String,
        source: String,
        category: String,
        pageNumber: Int?,
        keywords: List<String>,
        plainText: String,
        blocksJson: String?
    ): String {
        return TextSanitizer.normalizeForSearch(
            buildSearchPayload(
                title = title,
                source = source,
                category = category,
                pageNumber = pageNumber,
                keywords = keywords,
                plainText = plainText,
                blocksJson = blocksJson
            )
        )
    }

    fun analyze(blocksJson: String?, fallbackPlainText: String = "", query: String = ""): SemanticRoleProfile {
        val root = blocksJson?.takeIf { it.isNotBlank() }?.let { BlocksJsonUtils.parseRoot(it) }
        val collected = collectRoleTexts(root)
        val figureSignals = collectFigureNodeSignals(root, query)
        if (collected.totalBlocks == 0) {
            return SemanticRoleProfile(
                totalBlocks = if (fallbackPlainText.isBlank()) 0 else 1,
                bodyCount = if (fallbackPlainText.isBlank()) 0 else 1,
                dominantRole = if (fallbackPlainText.isBlank()) "" else "body",
                lowValueOnly = false
            )
        }

        val counts = collected.roleCounts
        val dominant = counts.maxByOrNull { it.value }?.key.orEmpty()
        val primaryCount = (counts["heading"] ?: 0) + (counts["body"] ?: 0) + (counts["table"] ?: 0)
        return SemanticRoleProfile(
            totalBlocks = collected.totalBlocks,
            headingCount = counts["heading"] ?: 0,
            bodyCount = counts["body"] ?: 0,
            tableCount = counts["table"] ?: 0,
            tableNoteCount = counts["table_note"] ?: 0,
            captionCount = counts["caption"] ?: 0,
            artifactCount = counts["artifact"] ?: 0,
            figureCount = counts["figure"] ?: 0,
            figureNodeCount = counts["figure_node"] ?: 0,
            dominantRole = dominant,
            lowValueOnly = primaryCount == 0 && (counts["figure_node"] ?: 0) == 0 && collected.totalBlocks > 0,
            figureLabelMatchCount = figureSignals.labelMatchCount,
            figureCaptionMatchCount = figureSignals.captionMatchCount,
            matchedFigureLabel = figureSignals.matchedLabel,
            matchedFigureCaption = figureSignals.matchedCaption,
            matchedCanonicalFigureNodeId = figureSignals.matchedCanonicalId
        )
    }

    private data class CollectedRoleTexts(
        val roleTexts: LinkedHashMap<String, MutableList<String>>,
        val roleCounts: MutableMap<String, Int>,
        val totalBlocks: Int
    )

    private data class FigureNodeSignals(
        val labelMatchCount: Int = 0,
        val captionMatchCount: Int = 0,
        val matchedLabel: String = "",
        val matchedCaption: String = "",
        val matchedCanonicalId: String = ""
    )

    private fun buildWeightedPlainText(blocksJson: String?, fallbackPlainText: String): String {
        val root = blocksJson?.takeIf { it.isNotBlank() }?.let { BlocksJsonUtils.parseRoot(it) }
        val collected = collectRoleTexts(root)
        val weighted = mutableListOf<String>()
        val fallback = normalizeWs(fallbackPlainText)

        for ((role, weight) in roleWeights) {
            val texts = collected.roleTexts[role].orEmpty()
            if (texts.isEmpty() || weight <= 0) continue
            val joined = texts.joinToString("\n")
            repeat(weight) { weighted += joined }
        }

        val lowValueOnly = ((collected.roleCounts["heading"] ?: 0) + (collected.roleCounts["body"] ?: 0) + (collected.roleCounts["table"] ?: 0)) == 0
        if (fallback.isNotBlank() && (weighted.isEmpty() || (lowValueOnly && looksLikeBodyFallback(fallback)))) {
            weighted += fallback
        }

        return weighted.joinToString("\n\n").trim()
    }

    private fun collectRoleTexts(root: JsonElement?): CollectedRoleTexts {
        val roleTexts = linkedMapOf<String, MutableList<String>>()
        val roleCounts = linkedMapOf<String, Int>()
        root ?: return CollectedRoleTexts(roleTexts, roleCounts, 0)
        val blocks = BlocksJsonUtils.extractBlocksArray(root)

        val figureNodes = if (root.isJsonObject) {
            val obj = root.asJsonObject
            val keys = listOf("figureNodes", "figure_nodes")
            var found: JsonArray? = null
            for (k in keys) {
                val v = obj.get(k)
                if (v != null && v.isJsonArray) {
                    found = v.asJsonArray
                    break
                }
            }
            found
        } else null

        var totalBlocks = 0
        if (blocks != null) {
            for (i in 0 until blocks.size()) {
                val block = blocks.get(i)
                val role = inferRole(block)
                roleCounts[role] = (roleCounts[role] ?: 0) + 1
                addRoleText(roleTexts, role, extractText(block))
                totalBlocks++
            }
        }

        if (figureNodes != null) {
            for (i in 0 until figureNodes.size()) {
                val figureNode = figureNodes.get(i)
                roleCounts["figure_node"] = (roleCounts["figure_node"] ?: 0) + 1
                addRoleText(roleTexts, "figure_node", extractFigureNodeText(figureNode))
                totalBlocks++
            }
        }

        if (totalBlocks > 0) {
            return CollectedRoleTexts(roleTexts, roleCounts, totalBlocks)
        }

        val role = inferRole(root)
        roleCounts[role] = 1
        addRoleText(roleTexts, role, extractText(root))
        return CollectedRoleTexts(roleTexts, roleCounts, 1)
    }

    private fun collectFigureNodeSignals(root: JsonElement?, query: String): FigureNodeSignals {
        val figureNodes = if (root != null && root.isJsonObject) {
            val obj = root.asJsonObject
            val keys = listOf("figureNodes", "figure_nodes")
            var found: JsonArray? = null
            for (k in keys) {
                val v = obj.get(k)
                if (v != null && v.isJsonArray) {
                    found = v.asJsonArray
                    break
                }
            }
            found
        } else null

        if (figureNodes == null) return FigureNodeSignals()

        val normalizedQuery = TextSanitizer.normalizeForSearch(query).lowercase()
        val noSpaceQuery = normalizedQuery.replace(Regex("\\s+"), "")
        if (normalizedQuery.isBlank()) {
            return FigureNodeSignals()
        }

        var labelMatches = 0
        var captionMatches = 0
        var matchedLabel = ""
        var matchedCaption = ""
        var matchedCanonicalId = ""
        for (i in 0 until figureNodes.size()) {
            val node = figureNodes.get(i)
            val obj = node.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val canonical = obj.get("canonicalFigureNode")?.takeIf { it.isJsonObject }?.asJsonObject
            val label = normalizeWs(
                firstNonBlank(
                    obj.get("label")?.takeIf { it.isJsonPrimitive }?.asString,
                    canonical?.get("label")?.takeIf { it.isJsonPrimitive }?.asString
                ).orEmpty()
            )
            val caption = normalizeWs(extractFigureNodeText(node))
            val canonicalId = firstNonBlank(
                obj.get("canonicalFigureNodeId")?.takeIf { it.isJsonPrimitive }?.asString,
                canonical?.get("id")?.takeIf { it.isJsonPrimitive }?.asString
            ).orEmpty()

            if (matchesQuery(label, normalizedQuery, noSpaceQuery)) {
                labelMatches++
                if (matchedLabel.isBlank()) matchedLabel = label
                if (matchedCanonicalId.isBlank()) matchedCanonicalId = canonicalId
            }
            if (matchesQuery(caption, normalizedQuery, noSpaceQuery)) {
                captionMatches++
                if (matchedCaption.isBlank()) matchedCaption = caption
                if (matchedCanonicalId.isBlank()) matchedCanonicalId = canonicalId
            }
        }

        return FigureNodeSignals(
            labelMatchCount = labelMatches,
            captionMatchCount = captionMatches,
            matchedLabel = matchedLabel,
            matchedCaption = matchedCaption,
            matchedCanonicalId = matchedCanonicalId
        )
    }

    private fun inferRole(element: JsonElement?): String {
        if (element == null || !element.isJsonObject) return "body"
        val obj = element.asJsonObject
        val explicit = obj.get("semanticRole")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lowercase().orEmpty()
        if (explicit.isNotBlank()) return explicit
        if (obj.get("imageUris")?.isJsonArray == true || obj.get("figureNodeId") != null) return "figure_node"
        return when (obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lowercase().orEmpty()) {
            "table" -> "table"
            "image" -> "figure"
            else -> "body"
        }
    }

    private fun extractFigureNodeText(element: JsonElement?): String {
        val obj = element?.takeIf { it.isJsonObject }?.asJsonObject ?: return extractText(element)
        val canonical = obj.get("canonicalFigureNode")?.takeIf { it.isJsonObject }?.asJsonObject
        val parts = linkedSetOf<String>()
        normalizeWs(firstNonBlank(
            obj.get("label")?.takeIf { it.isJsonPrimitive }?.asString,
            canonical?.get("label")?.takeIf { it.isJsonPrimitive }?.asString
        ).orEmpty())
            .takeIf { it.isNotBlank() }
            ?.let(parts::add)
        normalizeWs(firstNonBlank(
            obj.get("resolvedCaption")?.takeIf { it.isJsonPrimitive }?.asString,
            obj.get("caption")?.takeIf { it.isJsonPrimitive }?.asString,
            canonical?.get("resolvedCaption")?.takeIf { it.isJsonPrimitive }?.asString,
            canonical?.get("caption")?.takeIf { it.isJsonPrimitive }?.asString
        ).orEmpty())
            .takeIf { it.isNotBlank() }
            ?.let(parts::add)
        val captionTexts = obj.get("captionTexts")
        if (captionTexts != null && captionTexts.isJsonArray) {
            val arr = captionTexts.asJsonArray
            for (i in 0 until arr.size()) {
                val text = extractText(arr.get(i))
                if (text.isNotBlank()) parts.add(normalizeWs(text))
            }
        }
        val canonicalCaptionTexts = canonical?.get("captionTexts")
        if (canonicalCaptionTexts != null && canonicalCaptionTexts.isJsonArray) {
            val arr = canonicalCaptionTexts.asJsonArray
            for (i in 0 until arr.size()) {
                val text = extractText(arr.get(i))
                if (text.isNotBlank()) parts.add(normalizeWs(text))
            }
        }
        return parts.joinToString(" ")
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun matchesQuery(text: String, normalizedQuery: String, noSpaceQuery: String): Boolean {
        if (text.isBlank() || normalizedQuery.isBlank()) return false
        val normalizedText = TextSanitizer.normalizeForSearch(text).lowercase()
        if (normalizedText.contains(normalizedQuery)) return true
        if (noSpaceQuery.isBlank()) return false
        val noSpaceText = normalizedText.replace(Regex("\\s+"), "")
        return noSpaceText.contains(noSpaceQuery)
    }

    private fun addRoleText(target: LinkedHashMap<String, MutableList<String>>, role: String, text: String) {
        val clean = normalizeWs(text)
        if (clean.isBlank()) return
        val bucket = target.getOrPut(role) { mutableListOf() }
        if (!bucket.contains(clean)) {
            bucket += clean
        }
    }

    private fun extractText(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        if (element.isJsonPrimitive) {
            return if (element.asJsonPrimitive.isString) element.asString else ""
        }
        if (element.isJsonArray) {
            val arr = element.asJsonArray
            val joined = StringBuilder()
            for (i in 0 until arr.size()) {
                val text = extractText(arr.get(i))
                if (text.isNotBlank()) {
                    if (joined.isNotEmpty()) joined.append(" ")
                    joined.append(text)
                }
            }
            return joined.toString()
        }
        if (!element.isJsonObject) return ""

        val obj = element.asJsonObject
        val parts = mutableListOf<String>()
        for (key in contentKeys) {
            val value = obj.get(key)
            if (value != null) {
                val text = extractText(value)
                if (text.isNotBlank()) parts += text
            }
        }
        for (key in arrayKeys) {
            val value = obj.get(key)
            if (value != null) {
                val text = extractText(value)
                if (text.isNotBlank()) parts += text
            }
        }
        for (entry in obj.entrySet()) {
            val key = entry.key
            val value = entry.value
            if (metaKeys.contains(key.trim().lowercase()) || key in contentKeys || key in arrayKeys) continue
            val text = extractText(value)
            if (text.isNotBlank()) parts += text
        }
        return parts.joinToString(" ")
    }

    private fun looksLikeBodyFallback(text: String): Boolean {
        if (text.length >= 40) return true
        if (Regex("[绗?锛圿\\d+[)锛塢").containsMatchIn(text)) return true
        if (Regex("[銆傦紱锛?;]").findAll(text).count() >= 2) return true
        if (Regex("[\\u4e00-\\u9fff]").findAll(text).count() >= 20) return true
        return false
    }

    private fun normalizeWs(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }
}
