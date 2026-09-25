package com.example.powerai.domain.query

object QueryRewritePlanner {
    private data class ActionRewriteParts(
        val subject: String,
        val verb: String,
        val obj: String
    )

    private val conditionActionPhrases = listOf(
        "停止运行",
        "停运"
    )

    private val actionVerbs = listOf(
        "停止运行",
        "更换",
        "切断",
        "拉开",
        "合上",
        "倒闸",
        "投运",
        "停运",
        "停电",
        "送电",
        "检修",
        "巡视",
        "检查",
        "测量",
        "试验",
        "接地",
        "拆除",
        "安装"
    )

    private val conditionPhrases = listOf(
        "在什么情况下",
        "什么情况下",
        "啥情况下",
        "哪些情况下",
        "何种情况下",
        "在何种情况下",
        "啥情况",
        "什么情形下",
        "在什么情形下"
    )

    private val leadingPhrases = listOf(
        "请问",
        "我想问一下",
        "想问一下",
        "我想问",
        "问一下"
    )

    private val definitionSuffixes = listOf(
        "主要用途",
        "用途",
        "作用",
        "功能",
        "定义",
        "含义",
        "概念",
        "原理",
        "目的",
        "特点"
    )

    private val definitionPrefixes = listOf(
        "什么是",
        "何谓",
        "啥是",
        "什么叫",
        "什么叫做"
    )

    fun normalizeQuery(query: String, intentHint: QueryIntent? = null): String {
        val normalized = query
            .replace(Regex("[？?！!。，“”,,；;：:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return applyQueryAliases(normalized, intentHint)
    }

    fun buildRetrievalQueries(query: String): List<String> {
        val base = normalizeQuery(query)
        if (base.isBlank()) return emptyList()

        val variants = linkedSetOf<String>()
        addVariant(variants, base)

        val stripped = stripQuestionScaffolding(base)
        addVariant(variants, stripped)
        addConditionActionVariants(variants, stripped)
        addActionRuleVariants(variants, stripped)
        addTrailingActionPhraseVariants(variants, stripped)
        addDefinitionPurposeVariants(variants, stripped)

        if (containsConditionPhrase(base)) {
            addVariant(variants, addConditionKeyword(stripped))
        }

        if (stripped.contains("停止运行")) {
            val stopRun = normalizeQuery(stripped.replace("停止运行", " 停运"))
            addVariant(variants, stopRun)
            addVariant(variants, addConditionKeyword(stopRun))
        }

        return variants.toList().take(8)
    }

    fun isConditionStyleQuery(query: String): Boolean {
        val normalized = normalizeQuery(query)
        if (normalized.isBlank()) return false
        return containsConditionPhrase(normalized) || conditionActionPhrases.any(normalized::contains)
    }

    fun isActionRuleStyleQuery(query: String): Boolean {
        val normalized = normalizeQuery(query)
        if (normalized.isBlank() || isConditionStyleQuery(normalized)) return false
        return extractActionRewriteParts(normalized) != null
    }

    fun isDefinitionStyleQuery(query: String): Boolean {
        val normalized = normalizeQuery(query)
        if (normalized.isBlank()) return false
        val compact = normalized.replace(" ", "")
        return definitionSuffixes.any { suffix ->
            compact.endsWith(suffix) || compact.contains("${suffix}是什么") || compact.contains("${suffix}是啥")
        } || definitionPrefixes.any(compact::startsWith) || QueryAliasCatalog.definitionAliases.any { compact.endsWith(it.pattern) }
    }

    fun extractDefinitionSubject(query: String): String? {
        val compact = normalizeQuery(query).replace(" ", "")
        if (compact.isBlank()) return null

        val prefix = definitionPrefixes.firstOrNull(compact::startsWith)
        if (prefix != null) {
            val subject = compact.removePrefix(prefix).removeSuffix("的").trim()
            return subject.takeIf { it.length >= 2 }
        }

        val alias = QueryAliasCatalog.definitionAliases.firstOrNull { compact.endsWith(it.pattern) }
        if (alias != null) {
            val subject = compact.removeSuffix(alias.pattern).removeSuffix("的").trim()
            return subject.takeIf { it.length >= 2 }
        }

        val suffix = definitionSuffixes.firstOrNull { token ->
            compact.contains("${token}是什么") || compact.contains("${token}是啥")
        }
        if (suffix != null) {
            val subject = compact.substringBefore(suffix).removeSuffix("的").trim()
            return subject.takeIf { it.length >= 2 }
        }

        val trailingSuffix = definitionSuffixes.firstOrNull { compact.endsWith(it) } ?: return null
        val subject = compact.removeSuffix(trailingSuffix).removeSuffix("的").trim()
        return subject.takeIf { it.length >= 2 }
    }

    fun extractDefinitionCue(query: String): String? {
        val compact = normalizeQuery(query).replace(" ", "")
        if (compact.isBlank()) return null
        val alias = QueryAliasCatalog.definitionAliases.firstOrNull { compact.endsWith(it.pattern) }
        if (alias != null) return alias.cue
        return definitionSuffixes.firstOrNull { compact.endsWith(it) || compact.contains("${it}是什么") || compact.contains("${it}是啥") }
            ?: if (definitionPrefixes.any(compact::startsWith)) "定义" else null
    }

    fun extractActionAnchors(query: String): List<String> {
        val normalized = normalizeQuery(query)
        val parts = extractActionRewriteParts(normalized) ?: return emptyList()
        return linkedSetOf<String>().apply {
            add(parts.verb)
            if (parts.obj.isNotBlank()) add(parts.obj)
            if (parts.obj.isNotBlank()) add(parts.verb + parts.obj)
            if (parts.subject.isNotBlank()) add(parts.subject + parts.verb)
            if (parts.subject.isNotBlank() && parts.obj.isNotBlank()) add(parts.subject + parts.verb + parts.obj)
            if (parts.subject.isNotBlank() && parts.obj.isNotBlank()) add(parts.verb + parts.subject + parts.obj)
        }.toList()
    }

    private fun stripQuestionScaffolding(text: String): String {
        var current = normalizeQuery(text)
        leadingPhrases.forEach { prefix ->
            if (current.startsWith(prefix)) {
                current = current.removePrefix(prefix).trim()
            }
        }
        conditionPhrases.forEach { phrase ->
            current = current.replace(phrase, " ")
        }
        current = current.replace(Regex("^(如何|怎么|怎样|咋|为何|为什么|是否|能否|可否)\\s*"), "")
        current = current.replace(Regex("\\s*(吗|呢|呀|啊)$"), "")
        return normalizeQuery(current)
    }

    private fun addConditionKeyword(text: String): String {
        if (text.isBlank()) return text
        return normalizeQuery(
            when {
                text.contains("条件") -> text
                text.contains("停运") -> "$text 条件"
                text.contains("停止运行") -> text.replace("停止运行", "停止运行 条件")
                else -> "$text 条件"
            }
        )
    }

    private fun containsConditionPhrase(text: String): Boolean {
        return conditionPhrases.any(text::contains)
    }

    private fun addConditionActionVariants(target: MutableSet<String>, text: String) {
        val normalized = normalizeQuery(text)
        conditionActionPhrases.forEach { phrase ->
            if (!normalized.contains(phrase)) return@forEach

            val spaced = normalizeQuery(normalized.replace(phrase, " $phrase"))
            addVariant(target, spaced)
            addVariant(target, addConditionKeyword(spaced))

            val subject = spaced.substringBefore(" $phrase").trim()
            if (subject.isNotBlank()) {
                addVariant(target, "$subject 在什么情况下 $phrase")
            }
        }
    }

    private fun addActionRuleVariants(target: MutableSet<String>, text: String) {
        val parts = extractActionRewriteParts(text) ?: return
        addVariant(target, "${parts.subject} ${parts.verb} ${parts.obj}")
        addVariant(target, "${parts.verb} ${parts.subject} ${parts.obj}")
        addVariant(target, "${parts.subject}${parts.verb}${parts.obj}")
        addVariant(target, "${parts.verb}${parts.subject}${parts.obj}")
        addVariant(target, "${parts.verb} ${parts.obj}")
        addVariant(target, "${parts.subject} ${parts.verb}")
    }

    private fun addTrailingActionPhraseVariants(target: MutableSet<String>, text: String) {
        val trailing = extractTrailingActionPhrase(text) ?: return
        addVariant(target, "${trailing.verb} ${trailing.subject}")
        addVariant(target, "${trailing.verb}${trailing.subject}")
    }

    private fun addDefinitionPurposeVariants(target: MutableSet<String>, text: String) {
        val normalized = normalizeQuery(text)
        val compact = normalized.replace(" ", "")
        if (compact.isBlank()) return

        val subjectFromQuestion = extractDefinitionSubject(normalized)
        val suffixFromQuestion = extractDefinitionCue(normalized)
        val subject = subjectFromQuestion ?: extractSuffixSubject(compact)
        val suffix = suffixFromQuestion ?: definitionSuffixes.firstOrNull { compact.endsWith(it) }

        if (subject.isNullOrBlank() || suffix.isNullOrBlank()) return

        when (suffix) {
            "主要用途", "用途", "作用", "功能", "目的", "特点" -> {
                addVariant(target, "$subject $suffix")
                addVariant(target, "$subject 的主要用途是什么")
                addVariant(target, "$subject 的作用是什么")
                addVariant(target, "$subject 有什么用途")
                addVariant(target, "$subject 功能")
            }

            "定义", "含义", "概念" -> {
                addVariant(target, "$subject 是什么")
                addVariant(target, "$subject 的定义")
                addVariant(target, "$subject 的含义")
                addVariant(target, "$subject 概念")
            }

            "原理" -> {
                addVariant(target, "$subject 原理")
                addVariant(target, "$subject 的工作原理")
                addVariant(target, "$subject 是什么原理")
            }
        }

        addVariant(target, subject)
    }

    private fun extractSuffixSubject(compact: String): String? {
        val suffix = definitionSuffixes.firstOrNull { compact.endsWith(it) } ?: return null
        val subject = compact
            .removeSuffix(suffix)
            .removeSuffix("的")
            .trim()
        return subject.takeIf { it.length >= 2 }
    }

    private fun extractActionRewriteParts(query: String): ActionRewriteParts? {
        val compact = normalizeQuery(query).replace(" ", "")
        if (compact.length < 4) return null

        for (verb in actionVerbs.sortedByDescending { it.length }) {
            val index = compact.indexOf(verb)
            if (index <= 0 || index >= compact.lastIndex) continue

            val subject = trimSubject(compact.substring(0, index))
            val obj = trimObject(compact.substring(index + verb.length))
            if (subject.length >= 2 && obj.length >= 1) {
                return ActionRewriteParts(subject = subject, verb = verb, obj = obj)
            }
        }

        return null
    }

    private fun extractTrailingActionPhrase(query: String): ActionRewriteParts? {
        val compact = normalizeQuery(query).replace(" ", "")
        if (compact.length < 4) return null

        val trailingVerb = actionVerbs
            .sortedByDescending { it.length }
            .firstOrNull { verb -> compact.endsWith(verb) && compact.length > verb.length + 1 }
            ?: return null

        val subject = trimSubject(compact.removeSuffix(trailingVerb))
        if (subject.length < 2) return null

        return ActionRewriteParts(subject = subject, verb = trailingVerb, obj = "")
    }

    private fun applyQueryAliases(query: String, intentHint: QueryIntent?): String {
        var current = query
        QueryAliasCatalog.queryAliases
            .filter { alias ->
                when (intentHint) {
                    null -> alias.applyWhenIntentUnknown
                    else -> alias.intents.isEmpty() || alias.intents.contains(intentHint)
                }
            }
            .filter { alias -> alias.trigger.matches(current) }
            .forEach { alias ->
            current = replaceAliasSafely(current, alias)
        }
        return current
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun replaceAliasSafely(text: String, alias: QueryAlias): String {
        if (!alias.canonical.contains(alias.pattern)) {
            return text.replace(alias.pattern, alias.canonical)
        }

        val prefix = alias.canonical.substringBefore(alias.pattern)
        val suffix = alias.canonical.substringAfter(alias.pattern, "")

        val escapedPattern = Regex.escape(alias.pattern)
        val escapedPrefix = Regex.escape(prefix)
        val escapedSuffix = Regex.escape(suffix)
        val regex = when {
            prefix.isNotBlank() && suffix.isNotBlank() -> Regex("(?<!$escapedPrefix)$escapedPattern(?!$escapedSuffix)")
            prefix.isNotBlank() -> Regex("(?<!$escapedPrefix)$escapedPattern")
            suffix.isNotBlank() -> Regex("$escapedPattern(?!$escapedSuffix)")
            else -> Regex(escapedPattern)
        }
        return text.replace(regex, alias.canonical)
    }

    private fun trimSubject(raw: String): String {
        val compact = raw.replace(" ", "")
        val match = Regex("([\\u4e00-\\u9fffA-Za-z0-9]{2,10})$").find(compact) ?: return ""
        return match.value
    }

    private fun trimObject(raw: String): String {
        var compact = raw.replace(" ", "")
        listOf("什么", "哪些", "如何", "怎么", "怎样", "吗", "呢", "呀", "啊", "条件").forEach { marker ->
            val markerIndex = compact.indexOf(marker)
            if (markerIndex > 0) {
                compact = compact.substring(0, markerIndex)
            }
        }
        compact = compact.trim()
        val match = Regex("^([\\u4e00-\\u9fffA-Za-z0-9]{1,10})").find(compact) ?: return ""
        return match.value
    }

    private fun addVariant(target: MutableSet<String>, candidate: String) {
        val normalized = normalizeQuery(candidate)
        if (normalized.length >= 2) {
            target += normalized
        }
    }
}