package com.example.powerai.domain.usecase

import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.domain.query.QueryRewritePlanner

internal object LocalEvidenceHeuristics {
    val lowSignalTokens = setOf("什么", "情况", "怎么样", "哪些", "何种", "情形", "如何", "为何", "条件")
    val removableParticles = listOf("", "主要", "有关", "相关")
    val definitionSuffixes = listOf("主要用", "用", "作用", "功能", "定义", "含义", "概念", "原理", "目的", "特点")
    val stopRunAnchors = listOf("停止运行", "停运", "应立即停止运", "立即停止运行", "有下列情况之一")
    val procedureAnchors = listOf("步骤", "流程", "处置", "处理", "操作", "首先", "其次", "然后", "最")
    val comparisonAnchors = listOf("区别", "不同", "差异", "比较", "对比", "相同", "不同", "分别")
    val noisyMarkers = listOf("附件", "序号", "备注", "巡视", "配备标准")

    fun chineseNumberToInt(text: String): Int? {
        if (text.isBlank()) return null
        val digits = mapOf(
            '零' to 0,
            '一' to 1,
            '二' to 2,
            '三' to 3,
            '四' to 4,
            '五' to 5,
            '六' to 6,
            '七' to 7,
            '八' to 8,
            '九' to 9
        )
        if (text.all { it in digits.keys }) {
            return text.fold(0) { acc, c -> acc * 10 + (digits[c] ?: return null) }
        }
        var result = 0
        var current = 0
        for (char in text) {
            when (char) {
                '千' -> {
                    result += (if (current == 0) 1 else current) * 1000
                    current = 0
                }
                '百' -> {
                    result += (if (current == 0) 1 else current) * 100
                    current = 0
                }
                '十' -> {
                    result += (if (current == 0) 1 else current) * 10
                    current = 0
                }
                else -> current = digits[char] ?: return null
            }
        }
        return result + current
    }

    fun extractArticleSignature(title: String, content: String): String {
        val text = "$title $content"
        // 优先取显式条款引用（第X条），避免被问答序号、页码等无关数字干扰。
        val articleNumber = Regex("第\\s*([一二三四五六七八九十百千零0-9]+)\\s*条").find(text)?.groupValues?.getOrNull(1)
            ?: Regex("([一二三四五六七八九十百千零0-9]+)").find(text)?.groupValues?.getOrNull(1)
            ?: return ""
        val numeric = articleNumber.toIntOrNull() ?: chineseNumberToInt(articleNumber)
        return if (numeric != null) "第${numeric}条" else articleNumber
    }

    fun isExactLike(query: String, result: RetrievalResult, normalized: String): Boolean {
        val actionAnchors = QueryRewritePlanner.extractActionAnchors(query)
        val stopRunQuery = query.contains("停止运行") || query.contains("停运")
        return result.debug?.get("fts_bonus_applied") == true ||
            (stopRunQuery && normalized.contains("应立即停止运")) ||
            (stopRunQuery && normalized.contains("有下列情况之一")) ||
            actionAnchors.any { anchor -> anchor.length >= 4 && normalized.contains(anchor) }
    }

    fun isQuestionStyleAnswer(title: String, content: String): Boolean {
        return title.contains("哪些情况") || title.contains("什么情况下") || content.contains("答：")
    }

    fun isHandbookQaEntry(title: String, content: String): Boolean {
        val titleLooksLikeQuestion = title.contains("什么") || title.contains("用途") || title.contains("作用") || title.contains("定义") || title.contains("原理")
        val numberedQa = Regex("^[0-9]+[.．、]").containsMatchIn(title.trim())
        return content.contains("答：") && (titleLooksLikeQuestion || numberedQa)
    }

    fun expandCompactChineseToken(token: String): List<String> {
        val normalized = token.trim()
        if (normalized.isBlank()) return emptyList()

        val expanded = linkedSetOf<String>()
        expanded += normalized

        val compact = normalized.replace(" ", "")
        if (!compact.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) {
            return expanded.toList()
        }

        val particleStripped = removableParticles.fold(compact) { acc, marker -> acc.replace(marker, "") }
        if (particleStripped.length >= 2) {
            expanded += particleStripped
        }

        definitionSuffixes.forEach { suffix ->
            val subject = when {
                compact.endsWith(suffix) && compact.length > suffix.length -> compact.removeSuffix(suffix)
                compact.contains("${suffix}是什") -> compact.substringBefore(suffix)
                compact.contains("${suffix}是啥") -> compact.substringBefore(suffix)
                else -> ""
            }.removeSuffix("").trim()

            if (subject.length >= 2) {
                expanded += subject
                expanded += suffix
                if (suffix == "主要用") expanded += "用"
            }
        }

        if (compact.endsWith("是什") && compact.length > 4) {
            val subject = compact.removeSuffix("是什么").removeSuffix("啥").trim()
            if (subject.length >= 2) {
                expanded += subject
            }
        }

        return expanded.toList()
    }

    fun isDefinitionSuffixToken(token: String): Boolean {
        return token in definitionSuffixes
    }
}
