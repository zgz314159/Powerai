package com.example.powerai.core.model

/**
 * 来源资格：可追溯/已审核/适用场景是不同状态。
 * 简短内置摘要在未核定前不得作为规范操作回答的权威证据（仍可检索浏览并明确标注）。
 */
object SourceQualification {
    const val UNVERIFIED_SYNOPSIS_LABEL = "内置安规摘要（来源未核定）"

    /** Import policy bump forces Room rows to refresh labels even when asset bytes unchanged. */
    const val POWER_SAFETY_RULES_POLICY = "srcpol-2"

    const val POWER_SAFETY_RULES_ASSET = "power_safety_rules.json"

    /** Same scheme as [ImportUtils.sha256Hex]("asset:…") consumers — keep in sync with importer. */
    const val POWER_SAFETY_RULES_ASSET_KEY = "asset:$POWER_SAFETY_RULES_ASSET"

    fun isUnverifiedSynopsisLabel(source: String?): Boolean {
        val s = source?.trim().orEmpty()
        if (s.isEmpty()) return false
        if (s == UNVERIFIED_SYNOPSIS_LABEL) return true
        if (s.contains("来源未核定")) return true
        // legacy false formal name used before srcpol-2
        if (s == "电力安全工作规程") return true
        if (s.equals("builtin", ignoreCase = true)) return true
        return false
    }

    fun isUnverifiedSynopsis(item: KnowledgeItem): Boolean {
        if (isUnverifiedSynopsisLabel(item.source)) return true
        if (item.category == "builtin-synopsis") return true
        return false
    }

    /** 规范操作/规程类回答仅允许非未核定摘要。 */
    fun isQualifiedForNormativeAnswer(item: KnowledgeItem): Boolean = !isUnverifiedSynopsis(item)

    fun partitionForNormativeAnswer(items: List<KnowledgeItem>): Pair<List<KnowledgeItem>, List<KnowledgeItem>> {
        val qualified = mutableListOf<KnowledgeItem>()
        val unverified = mutableListOf<KnowledgeItem>()
        for (item in items) {
            if (isQualifiedForNormativeAnswer(item)) qualified += item else unverified += item
        }
        return qualified to unverified
    }

    fun unverifiedSynopsisNotice(items: List<KnowledgeItem>): String {
        val titles = items.map { it.title }.filter { it.isNotBlank() }.distinct().take(3)
        return buildString {
            append("仅检索到来源未核定的内置摘要，不能作为正式规程执行依据。")
            if (titles.isNotEmpty()) {
                append("相关摘要标题：")
                append(titles.joinToString("、"))
                append("。")
            }
            append("请改用已导入的正式知识包提问，或补充可核验来源后再问。")
        }
    }
}
