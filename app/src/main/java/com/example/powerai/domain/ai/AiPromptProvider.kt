package com.example.powerai.domain.ai

object AiPromptProvider {
    private const val basePrompt = "你是电力线路工助手，只能根据提供的本地技规回答问题。未明确规定请回答‘技规未明确规定’。"

    private const val aiOnlyBasePrompt = "你是电力线路工助手。请直接回答用户问题；当信息不足或不确定时，请明确说明不确定，并给出可验证的检查步骤或参考方向。"

    private fun buildDeviceDateRules(deviceToday: String?): String {
        val day = deviceToday?.trim().orEmpty()
        if (day.isBlank()) return ""
        return """
            系统信息（来自设备系统时间）：今天日期是 $day。

            规则补充：
            - 若用户询问“今天/现在/今年/当前日期”，以该系统信息为准。
            - 你不具备浏览器/联网检索工具，除非用户在对话中提供了检索结果/网页内容/新闻原文等外部材料。
            - 若用户要求实时信息但未提供外部材料，请明确说明无法确认，并建议用户提供来源或开启检索能力后再总结。
            - 不要声称你的知识截至某一年（例如‘截至2024’）。
        """.trimIndent()
    }
    fun getPromptWithContext(contextText: String, question: String): String {
        return "$basePrompt\n\n技规内容：$contextText\n\n问题：$question"
    }

    fun buildReferenceWithCitationRules(numberedEvidence: String): String {
        return buildReferenceWithCitationRules(numberedEvidence = numberedEvidence, deviceToday = null)
    }

    fun buildReferenceWithCitationRules(numberedEvidence: String, deviceToday: String?): String {
        val dateRules = buildDeviceDateRules(deviceToday)
        return """
            $basePrompt

            ${if (dateRules.isNotBlank()) dateRules + "\n\n" else ""}你将收到一份“证据列表”，每条证据都有编号：[1]、[2] ...。
            请严格遵守：
            1) 只能基于证据列表回答，不要编造任何不在证据中的事实。
            2) 每条关键结论句末必须使用引用编号，格式如：[1] 或 [1][2]。
            3) 只能使用证据列表中存在的编号；不要输出列表之外的编号。
            4) 若证据不足以回答，请回答“技规未明确规定”。
            5) 关于“系统信息”里的日期（今天/今年），可以直接使用该日期，不需要引用编号。

            证据列表：
            $numberedEvidence
        """.trimIndent()
    }

    fun buildAiOnlyPrompt(): String {
        return aiOnlyBasePrompt
    }

    fun buildAiOnlyPrompt(deviceToday: String?): String {
        val dateRules = buildDeviceDateRules(deviceToday)
        return if (dateRules.isBlank()) {
            aiOnlyBasePrompt
        } else {
            """
                $aiOnlyBasePrompt

                $dateRules
            """.trimIndent()
        }
    }

    private fun buildLocalEvidenceSection(localEvidence: String?): String {
        val local = localEvidence?.trim().orEmpty()
        if (local.isBlank()) return ""
        return """

            你还会收到“本地知识库证据”（来自应用内知识库）。
            请优先使用本地知识证据回答与电力专业相关的问题；若与联网结果冲突，请明确标注差异并给出保守结论。

            本地知识库证据：
            $local
        """.trimIndent()
    }

    fun buildAiOnlyPrompt(deviceToday: String?, localEvidence: String?): String {
        return buildAiOnlyPrompt(deviceToday) + buildLocalEvidenceSection(localEvidence)
    }

    fun buildAiWebSearchPrompt(deviceToday: String?, searchResults: String): String {
        return buildAiWebSearchPrompt(deviceToday = deviceToday, searchResults = searchResults, localEvidence = null)
    }

    fun buildAiWebSearchPrompt(deviceToday: String?, searchResults: String, localEvidence: String?): String {
        val dateRules = buildDeviceDateRules(deviceToday)
        val results = searchResults.trim()
        val localSection = buildLocalEvidenceSection(localEvidence)
        return """
            $aiOnlyBasePrompt

            ${if (dateRules.isNotBlank()) dateRules + "\n\n" else ""}
            你将收到一份由系统提供的“联网检索结果”（包含标题、摘要、URL）。
            请严格遵守：
            1) 只能基于检索结果回答，不要编造任何不在检索结果中的事实（尤其是实时新闻细节）。
            2) 若检索结果不足以支持结论，请明确说明“无法从当前检索结果确认”。
            3) 在回答末尾输出“来源：”并列出 3-5 条 URL（必须是检索结果中的 URL，保持原样，便于点击）。

            ${if (localSection.isNotBlank()) localSection else ""}

            联网检索结果：
            ${if (results.isBlank()) "（空）" else results}
        """.trimIndent()
    }
}
