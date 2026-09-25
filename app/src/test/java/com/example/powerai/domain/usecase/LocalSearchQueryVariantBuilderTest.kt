package com.example.powerai.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchQueryVariantBuilderTest {

    @Test
    fun `build rewrites fact question into stop-run focused variants`() {
        val variants = LocalSearchQueryVariantBuilder.build("变压器在什么情况下停止运行？")

        assertEquals("变压器在什么情况下停止运行", variants.first())
        assertTrue(variants.toString(), variants.any { it.contains("停止运行") && it.contains("变压器") })
        assertTrue(variants.toString(), variants.any { it.contains("条件") && it.contains("停止运行") })
        assertTrue(variants.toString(), variants.any { it.contains("变压器") && it.contains("停运") })
    }

    @Test
    fun `build rewrites short action phrase into condition-style variants`() {
        val variants = LocalSearchQueryVariantBuilder.build("变压器停止运行")

        assertTrue(variants.contains("变压器停止运行"))
        assertTrue(variants.toString(), variants.any { it.contains("停止运行") && it.contains("变压器") })
        assertTrue(variants.toString(), variants.any { it.contains("条件") && it.contains("停止运行") })
        assertTrue(variants.toString(), variants.any { it.contains("变压器") && it.contains("停运") })
    }

    @Test
    fun `build plan rewrites trailing action phrase variants`() {
        val plan = LocalSearchQueryVariantBuilder.buildPlan("发电机检修")

        assertEquals("发电机检修", plan.first().text)
        assertTrue(plan.toString(), plan.any { it.text == "检修 发电机" })
        assertTrue(plan.toString(), plan.any { it.text == "检修发电机" })
    }

    @Test
    fun `build plan adds subject and qa hints for definition query`() {
        val plan = LocalSearchQueryVariantBuilder.buildPlan("隔离开关用途")

        assertEquals("隔离开关用途", plan.first().text)
        assertTrue(plan.any { it.strategy == "definition_subject" && it.text == "隔离开关" })
        assertTrue(plan.any { it.strategy == "definition_question" && it.text.contains("隔离开关 的") && it.text.contains("用途") })
        assertTrue(plan.any { it.strategy == "qa_answer_hint" && it.text == "隔离开关 答" })
        assertTrue(plan.any { it.strategy == "answer_oriented" && it.text.contains("隔离开关 的") && it.text.endsWith("是") })
        assertTrue(plan.any { it.strategy == "answer_oriented" && it.text.contains("隔离开关 主要用于") || it.text.contains("隔离开关 用于") })
    }

    @Test
    fun `build plan adds procedure-oriented variants`() {
        val plan = LocalSearchQueryVariantBuilder.buildPlan("如何分析试验数据")

        assertTrue(plan.any { it.strategy == "procedure_outline" && it.text.contains("分析试验数据 操作步骤") })
        assertTrue(plan.any { it.strategy == "answer_oriented" && it.text.contains("分析试验数据 先 后") })
    }

    @Test
    fun `build plan adds comparison-oriented variants`() {
        val plan = LocalSearchQueryVariantBuilder.buildPlan("变压器和断路器的区别")

        assertTrue(plan.any { it.strategy == "comparison_outline" && it.text.contains("变压器 与 断路器 的区别") || it.text.contains("变压器 断路器 区别") })
        assertTrue(plan.any { it.strategy == "answer_oriented" && it.text.contains("变压器 与 断路器 相比") })
    }

    @Test
    fun `build plan adds prefix definition variants`() {
        val plan = LocalSearchQueryVariantBuilder.buildPlan("什么是跨步电压")

        assertTrue(plan.toString(), plan.any { it.strategy == "definition_subject" && it.text == "跨步电压" })
        assertTrue(plan.toString(), plan.any { it.strategy == "definition_question" && it.text.contains("跨步电压 的定义是什么") })
        assertTrue(plan.toString(), plan.any { it.strategy == "answer_oriented" && it.text.contains("跨步电压 是指") })
    }

    @Test
    fun `build plan adds colloquial definition variants`() {
        val plan = LocalSearchQueryVariantBuilder.buildPlan("隔离开关有啥用")

        assertTrue(plan.toString(), plan.any { it.strategy == "definition_subject" && it.text == "隔离开关" })
        assertTrue(plan.toString(), plan.any { it.strategy == "definition_question" && it.text.contains("隔离开关 的用途是什么") })
        assertTrue(plan.toString(), plan.any { it.strategy == "answer_oriented" && it.text.contains("隔离开关 用于") })
    }

    @Test
    fun `build plan normalizes colloquial procedure and comparison variants`() {
        val procedurePlan = LocalSearchQueryVariantBuilder.buildPlan("试验数据怎么分析")
        val comparisonPlan = LocalSearchQueryVariantBuilder.buildPlan("变压器跟断路器有啥区别")

        assertTrue(procedurePlan.toString(), procedurePlan.any { it.strategy == "procedure_outline" && it.text.contains("分析试验数据 操作步骤") })
        assertTrue(comparisonPlan.toString(), comparisonPlan.any { it.strategy == "comparison_outline" && it.text.contains("变压器 与 断路器 的区别") })
    }

    @Test
    fun `build plan normalizes shorthand condition and comparison variants`() {
        val conditionPlan = LocalSearchQueryVariantBuilder.buildPlan("变压器啥情况停运")
        val procedurePlan = LocalSearchQueryVariantBuilder.buildPlan("试验数据咋分析")
        val comparisonPlan = LocalSearchQueryVariantBuilder.buildPlan("变压器和断路器有啥不一样")

        assertTrue(conditionPlan.toString(), conditionPlan.any { it.strategy == "answer_oriented" && it.text.contains("变压器 停运条件") })
        assertTrue(procedurePlan.toString(), procedurePlan.any { it.strategy == "procedure_outline" && it.text.contains("分析试验数据 操作步骤") })
        assertTrue(comparisonPlan.toString(), comparisonPlan.any { it.strategy == "comparison_outline" && it.text.contains("变压器 与 断路器 的区别") || it.text.contains("变压器 断路器 区别") })
    }

    @Test
    fun `build plan normalizes aliases and misspellings`() {
        val aliasDefinitionPlan = LocalSearchQueryVariantBuilder.buildPlan("隔开有啥用")
        val aliasDefinitionPlan2 = LocalSearchQueryVariantBuilder.buildPlan("跨步压是啥意思")
        val misspelledComparisonPlan = LocalSearchQueryVariantBuilder.buildPlan("变压器和断电器有啥不一样")

        assertTrue(aliasDefinitionPlan.toString(), aliasDefinitionPlan.any { it.text.contains("隔离开关") })
        assertTrue(aliasDefinitionPlan2.toString(), aliasDefinitionPlan2.any { it.text.contains("跨步电压") })
        assertTrue(misspelledComparisonPlan.toString(), misspelledComparisonPlan.any { it.text.contains("断路器") })
    }
}