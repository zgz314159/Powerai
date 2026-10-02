package com.example.powerai.core.model.util

import com.example.powerai.core.model.util.BlocksJsonUtils.asJsonObjectOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.intOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.stringOrNull
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Resolves a table's title/label block to the table block it labels.
 *
 * PaddleModels (and similar sources) frequently emit a table's caption/title as a
 * separate non-table block sitting immediately above the table, and sometimes in a
 * different knowledge entry than the table itself. When a search or citation hit
 * lands on that label, the detail block selection and the "在 PDF 中定位" target must
 * still be the table, not the caption line.
 *
 * The association is inferred spatially (same page, table directly below the label),
 * which is the only signal present in the KB.
 */
object TableLabelBlockResolver {
    /** Maximum vertical distance (PDF points) allowed between the label and its table. */
    const val MAX_LABEL_GAP = 36f

    /** Tolerance for a table that starts slightly above the label baseline. */
    private const val GAP_EPSILON = 1.5f

    /** Labels are short title lines, not paragraphs. */
    private const val MAX_LABEL_LENGTH = 60

    private const val BBOX_ARRAY_SIZE = 4
    private const val BBOX_TOP_INDEX = 1
    private const val BBOX_HEIGHT_INDEX = 3

    private val TABLE_LABEL = Regex("表\\s*\\d+")

    data class BlockInfo(
        val index: Int,
        val id: String?,
        val type: String,
        val semanticRole: String,
        val text: String,
        val page: Int?,
        val top: Float?,
        val bottom: Float?,
    )

    /** A table directly below a label, with its effective vertical gap (table top − label bottom). */
    data class TableBelow(val index: Int, val gap: Float)

    fun blockInfos(blocksJson: String?): List<BlockInfo> {
        val root =
            blocksJson?.takeIf { it.isNotBlank() }?.let { BlocksJsonUtils.parseRoot(it) }
                ?: return emptyList()
        val texts = BlocksTextExtractor.extractBlockPlainTexts(blocksJson)
        val array = BlocksJsonUtils.extractBlocksArray(root)
        return if (array != null) {
            val out = ArrayList<BlockInfo>(array.size())
            for (i in 0 until array.size()) {
                val obj = array.get(i).asJsonObjectOrNull() ?: continue
                out += infoFor(i, obj, texts.getOrNull(i).orEmpty())
            }
            out
        } else {
            val obj = root.asJsonObjectOrNull()
            if (obj == null) emptyList() else listOf(infoFor(0, obj, texts.firstOrNull().orEmpty()))
        }
    }

    /** A short caption/label line that names a table ("…表1" or a caption-role block). */
    fun isTableLabel(block: BlockInfo): Boolean {
        val text = block.text.trim()
        return block.type != "table" &&
            text.isNotEmpty() &&
            text.length <= MAX_LABEL_LENGTH &&
            (TABLE_LABEL.containsMatchIn(text) || block.semanticRole == "caption")
    }

    /** If [matchedIndex] labels a table below it in the same list, returns that table's index. */
    fun tableIndexForLabel(
        blocks: List<BlockInfo>,
        matchedIndex: Int,
    ): Int? {
        val label = blocks.getOrNull(matchedIndex) ?: return null
        return if (isTableLabel(label)) nearestTableBelowWithGap(blocks, label)?.index else null
    }

    /**
     * Nearest table directly below [label] on the same page, with its effective vertical
     * gap. Callers that only need the block use [TableBelow.index]; selecting across
     * sibling entries needs the gap so the closest table wins even when the DAO returns
     * candidates ordered by entity id.
     */
    fun nearestTableBelowWithGap(
        blocks: List<BlockInfo>,
        label: BlockInfo,
    ): TableBelow? {
        val page = label.page
        val labelBottom = label.bottom
        return if (page == null || labelBottom == null) {
            null
        } else {
            blocks.asSequence()
                .filter { it.type == "table" && it.page == page }
                .mapNotNull { block -> block.top?.let { top -> TableBelow(block.index, top - labelBottom) } }
                .filter { it.gap >= -GAP_EPSILON && it.gap <= MAX_LABEL_GAP }
                .minByOrNull { it.gap }
        }
    }

    private fun infoFor(
        index: Int,
        obj: JsonObject,
        text: String,
    ): BlockInfo {
        val (top, bottom) = readVerticalBounds(obj.get("bbox") ?: obj.get("boundingBox"))
        return BlockInfo(
            index = index,
            id = obj.stringOrNull("id") ?: obj.stringOrNull("blockId"),
            type = obj.stringOrNull("type")?.trim()?.lowercase().orEmpty(),
            semanticRole = obj.stringOrNull("semanticRole")?.trim()?.lowercase().orEmpty(),
            text = text,
            page = obj.intOrNull("pageNumber") ?: obj.intOrNull("page"),
            top = top,
            bottom = bottom,
        )
    }

    private fun readVerticalBounds(element: JsonElement?): Pair<Float?, Float?> =
        when {
            element == null || element.isJsonNull -> null to null
            element.isJsonObject -> verticalBoundsFromObject(element.asJsonObject)
            element.isJsonArray -> verticalBoundsFromArray(element.asJsonArray)
            else -> null to null
        }

    private fun verticalBoundsFromObject(obj: JsonObject): Pair<Float?, Float?> {
        val top = floatOf(obj.get("top")) ?: floatOf(obj.get("yMin"))
        val bottom =
            floatOf(obj.get("bottom"))
                ?: floatOf(obj.get("yMax"))
                ?: derivedBottom(top, floatOf(obj.get("height")))
        return top to bottom
    }

    private fun verticalBoundsFromArray(array: JsonArray): Pair<Float?, Float?> {
        return if (array.size() == BBOX_ARRAY_SIZE) {
            val top = floatOf(array.get(BBOX_TOP_INDEX))
            top to derivedBottom(top, floatOf(array.get(BBOX_HEIGHT_INDEX)))
        } else {
            null to null
        }
    }

    private fun derivedBottom(
        top: Float?,
        height: Float?,
    ): Float? = if (top != null && height != null) top + height else null

    private fun floatOf(element: JsonElement?): Float? {
        if (element == null || !element.isJsonPrimitive) return null
        val prim = element.asJsonPrimitive
        return when {
            prim.isNumber -> prim.asFloat
            prim.isString -> prim.asString.toFloatOrNull()
            else -> null
        }
    }
}
