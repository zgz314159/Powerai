package com.example.powerai.ui.blocks









import com.example.powerai.core.model.UnknownBlock
import com.example.powerai.core.model.FigureNodeBlock
import com.example.powerai.core.model.CodeBlock
import com.example.powerai.core.model.TableBlock
import com.example.powerai.core.model.ListBlock
import com.example.powerai.core.model.ImageBlock
import com.example.powerai.core.model.TextBlock
import com.example.powerai.core.model.KnowledgeBlock
import com.example.powerai.core.model.util.BlocksJsonUtils.sha1Hex
import com.example.powerai.core.model.util.BlocksJsonUtils

internal object BlocksIdGenerator {

    fun ensureStableIds(blocks: List<KnowledgeBlock>): List<KnowledgeBlock> {
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

    fun getBlockTypeString(block: KnowledgeBlock): String {
        return when (block) {
            is TextBlock -> "text"
            is ImageBlock -> "image"
            is FigureNodeBlock -> "figure-node"
            is ListBlock -> if (block.ordered) "ol" else "ul"
            is TableBlock -> "table"
            is CodeBlock -> "code"
            is UnknownBlock -> (block.type ?: "unknown")
        }
    }

    fun getBlockContentText(block: KnowledgeBlock): String {
        return when (block) {
            is TextBlock -> block.text
            is ImageBlock -> listOfNotNull(block.src, block.alt, block.caption).joinToString(" ")
            is FigureNodeBlock -> listOfNotNull(block.label, block.caption, block.images.joinToString(",")).joinToString(" ")
            is ListBlock -> block.items.joinToString("\n")
            is TableBlock -> block.rows.joinToString("\n") { row -> row.joinToString("\t") }
            is CodeBlock -> block.code
            is UnknownBlock -> block.rawText
        }
    }

    fun generateUniqueId(block: KnowledgeBlock, seen: MutableMap<String, Int>): String {
        val type = getBlockTypeString(block)
        val text = getBlockContentText(block)
        val norm = ("$type|$text").lowercase().replace(Regex("\\s+"), " ").trim()
        val base = sha1Hex(norm).take(12)
        val n = (seen[base] ?: 0) + 1
        seen[base] = n
        return "b_${base}_$n"
    }

    fun assignIdToBlock(block: KnowledgeBlock, id: String): KnowledgeBlock {
        return when (block) {
            is TextBlock -> block.copy(id = id)
            is ImageBlock -> block.copy(id = id)
            is FigureNodeBlock -> block.copy(id = id)
            is ListBlock -> block.copy(id = id)
            is TableBlock -> block.copy(id = id)
            is CodeBlock -> block.copy(id = id)
            is UnknownBlock -> block.copy(id = id)
        }
    }
}
