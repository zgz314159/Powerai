package com.example.powerai.ui.blocks

// android.util.Log removed per TODO order; tracing suppressed
import com.example.powerai.core.model.KnowledgeBlock
import com.example.powerai.core.model.TextBlock
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.ListBlock
import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.model.CodeBlock
import com.example.powerai.core.model.FigureNodeBlock
import com.example.powerai.core.model.UnknownBlock
import com.example.powerai.core.model.util.BlocksJsonUtils
import com.example.powerai.core.model.util.BlocksJsonUtils.asJsonObjectOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.booleanOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.boundingBoxAsStringOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.floatOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.intOrNull
import com.example.powerai.core.model.util.BlocksJsonUtils.sha1Hex
import com.example.powerai.core.model.util.BlocksJsonUtils.stringOrNull
import com.google.gson.JsonObject

object BlocksParser {
    private const val TAG = "PowerAi.Trace"

    fun parseBlocks(json: String?): List<KnowledgeBlock>? {
        if (json.isNullOrBlank()) {
            return null
        }

        val root = BlocksJsonUtils.parseRoot(json) ?: return null
        val blocksArray = BlocksJsonUtils.extractBlocksArray(root)

        if (blocksArray == null || blocksArray.size() == 0) {
            return emptyList()
        }

        val out = ArrayList<KnowledgeBlock>(blocksArray.size())
        for (el in blocksArray) {
            val obj = el.asJsonObjectOrNull() ?: continue
            out.add(parseBlockObject(obj))
        }
        return ensureStableIds(out)
    }

    private fun ensureStableIds(blocks: List<KnowledgeBlock>): List<KnowledgeBlock> {
        if (blocks.isEmpty()) return blocks

        val seen = HashMap<String, Int>(blocks.size)
        val out = ArrayList<KnowledgeBlock>(blocks.size)
        for (b in blocks) {
            if (!b.id.isNullOrBlank()) {
                out.add(b)
                continue
            }
            val id = generateUniqueId(b, seen)
            out.add(assignIdToBlock(b, id))
        }
        return out
    }

    private fun getBlockTypeString(block: KnowledgeBlock): String {
        return when (block) {
            is TextBlock -> "text"
            is ImageBlock -> "image"
            is ListBlock -> if (block.ordered) "ol" else "ul"
            is TableBlock -> "table"
            is CodeBlock -> "code"
            is FigureNodeBlock -> "figure"
            is UnknownBlock -> (block.type ?: "unknown")
        }
    }

    private fun getBlockContentText(block: KnowledgeBlock): String {
        return when (block) {
            is TextBlock -> block.text
            is ImageBlock -> listOfNotNull(block.src, block.alt, block.caption).joinToString(" ")
            is ListBlock -> block.items.joinToString("\n")
            is TableBlock -> block.rows.joinToString("\n") { row -> row.joinToString("\t") }
            is CodeBlock -> block.code
            is FigureNodeBlock -> listOfNotNull(block.caption, block.images.joinToString(",")).joinToString(" ")
            is UnknownBlock -> block.rawText
        }
    }

    private fun generateUniqueId(block: KnowledgeBlock, seen: MutableMap<String, Int>): String {
        val type = getBlockTypeString(block)
        val text = getBlockContentText(block)
        val norm = ("$type|$text").lowercase().replace(Regex("\\s+"), " ").trim()
        val base = sha1Hex(norm).take(12)
        val n = (seen[base] ?: 0) + 1
        seen[base] = n
        return "b_${base}_$n"
    }

    private fun assignIdToBlock(block: KnowledgeBlock, id: String): KnowledgeBlock {
        return when (block) {
            is TextBlock -> block.copy(id = id)
            is ImageBlock -> block.copy(id = id)
            is ListBlock -> block.copy(id = id)
            is TableBlock -> block.copy(id = id)
            is CodeBlock -> block.copy(id = id)
            is FigureNodeBlock -> block.copy(id = id)
            is UnknownBlock -> block.copy(id = id)
        }
    }

    private fun parseBlockObject(obj: JsonObject): KnowledgeBlock {
        val id = obj.stringOrNull("id") ?: obj.stringOrNull("blockId")
        val type = obj.stringOrNull("type") ?: obj.stringOrNull("blockType")
        val t = type?.trim()?.lowercase()

        val bbox = obj.boundingBoxAsStringOrNull()

        val pageNumber = obj.intOrNull("pageNumber")
            ?: obj.intOrNull("page")
            ?: obj.intOrNull("p")

        val imageUri = obj.stringOrNull("imageUri")
            ?: obj.stringOrNull("image_uri")
            ?: obj.stringOrNull("snapshotUri")
            ?: obj.stringOrNull("snapshot_uri")

        val pdfWidth = obj.floatOrNull("pdfWidth") ?: obj.floatOrNull("pdf_width")
        val pdfHeight = obj.floatOrNull("pdfHeight") ?: obj.floatOrNull("pdf_height")

        return when (t) {
            "p", "paragraph", "text" -> {
                val text = BlocksElementExtractors.extractText(obj)
                TextBlock(
                    id = id, text = text, style = TextBlock.TextStyle.Paragraph,
                    fontSize = obj.floatOrNull("fontSize"),
                    isBold = obj.booleanOrNull("isBold"),
                    alignment = obj.stringOrNull("alignment"),
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
            "h1", "heading1", "heading_1", "title" -> {
                TextBlock(
                    id = id, text = BlocksElementExtractors.extractText(obj), style = TextBlock.TextStyle.Heading1,
                    fontSize = obj.floatOrNull("fontSize"),
                    isBold = obj.booleanOrNull("isBold") ?: true,
                    alignment = obj.stringOrNull("alignment"),
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
            "h2", "heading2", "heading_2" -> {
                TextBlock(
                    id = id, text = BlocksElementExtractors.extractText(obj), style = TextBlock.TextStyle.Heading2,
                    fontSize = obj.floatOrNull("fontSize"),
                    isBold = obj.booleanOrNull("isBold") ?: true,
                    alignment = obj.stringOrNull("alignment"),
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
            "h3", "heading3", "heading_3" -> {
                TextBlock(
                    id = id, text = BlocksElementExtractors.extractText(obj), style = TextBlock.TextStyle.Heading3,
                    fontSize = obj.floatOrNull("fontSize"),
                    isBold = obj.booleanOrNull("isBold") ?: true,
                    alignment = obj.stringOrNull("alignment"),
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
            "quote", "blockquote" -> {
                TextBlock(
                    id = id, text = BlocksElementExtractors.extractText(obj), style = TextBlock.TextStyle.Quote,
                    fontSize = obj.floatOrNull("fontSize"),
                    isBold = obj.booleanOrNull("isBold"),
                    alignment = obj.stringOrNull("alignment"),
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
            "image", "img" -> {
                val src = obj.stringOrNull("src").orEmpty().ifBlank { obj.stringOrNull("url").orEmpty() }
                val alt = obj.stringOrNull("alt")
                val caption = obj.stringOrNull("caption")
                ImageBlock(
                    id = id, src = src, alt = alt, caption = caption,
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
            "list", "bullet_list", "ordered_list" -> {
                val ordered = when {
                    obj.booleanOrNull("ordered") != null -> obj.booleanOrNull("ordered") == true
                    t == "ordered_list" -> true
                    else -> false
                }
                val items = BlocksElementExtractors.extractListItems(obj)
                ListBlock(
                    id = id, ordered = ordered, items = items,
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
            "table" -> {
                val rows = BlocksElementExtractors.extractTableRows(obj)
                val cells = BlocksElementExtractors.extractTableCells(obj)
                TableBlock(
                    id = id, rows = rows, cells = cells,
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
            "code", "code_block" -> {
                val code = obj.stringOrNull("code").orEmpty().ifBlank { obj.stringOrNull("text").orEmpty() }
                val language = obj.stringOrNull("language")
                CodeBlock(
                    id = id, code = code, language = language,
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
            "figure" -> {
                val caption = obj.stringOrNull("caption")
                val images = mutableListOf<String>()
                obj.get("images")?.asJsonArray?.forEach { it.asString?.let { images.add(it) } }
                FigureNodeBlock(
                    id = id, caption = caption, images = images,
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
            else -> {
                val text = BlocksElementExtractors.extractText(obj)
                UnknownBlock(
                    id = id, type = type, rawText = text,
                    boundingBox = bbox, pageNumber = pageNumber, imageUri = imageUri,
                    pdfWidth = pdfWidth, pdfHeight = pdfHeight
                )
            }
        }
    }
}
