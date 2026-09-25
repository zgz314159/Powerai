package com.example.powerai.domain.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryUnderstandingPipelineTest {

    @Test
    fun `understand classifies topic overview query`() {
        val result = QueryUnderstandingPipeline.understand("变压器")

        assertEquals(QueryIntent.TOPIC_OVERVIEW, result.intent)
        assertEquals("变压器", result.normalizedQuery)
    }

    @Test
    fun `understand classifies stop-run question as fact query`() {
        val result = QueryUnderstandingPipeline.understand("变压器在什么情况下停止运行？")

        assertEquals(QueryIntent.FACT_QUESTION, result.intent)
        assertTrue(result.retrievalQueries.contains("变压器 停止运行"))
    }

    @Test
    fun `understand classifies short stop-run phrase as fact query and rewrites it`() {
        val result = QueryUnderstandingPipeline.understand("变压器停止运行")

        assertEquals(QueryIntent.FACT_QUESTION, result.intent)
        assertTrue(result.retrievalQueries.contains("变压器 在什么情况下 停止运行"))
        assertTrue(result.retrievalQueries.contains("变压器 停运"))
    }

    @Test
    fun `understand classifies process query`() {
        val result = QueryUnderstandingPipeline.understand("变压器更换流程")

        assertEquals(QueryIntent.PROCEDURE, result.intent)
    }

    @Test
    fun `understand classifies action rule query and rewrites fuse replacement variants`() {
        val result = QueryUnderstandingPipeline.understand("变压器更换熔丝")

        assertEquals(QueryIntent.FACT_QUESTION, result.intent)
        assertTrue(result.signals.contains("action_rule_style"))
        assertTrue(result.retrievalQueries.contains("变压器 更换 熔丝"))
        assertTrue(result.retrievalQueries.contains("更换 变压器 熔丝"))
    }

    @Test
    fun `understand classifies comparison query`() {
        val result = QueryUnderstandingPipeline.understand("隔离开关和断路器的区别")

        assertEquals(QueryIntent.COMPARISON, result.intent)
    }

    @Test
    fun `understand applies intent scoped aliases after classification`() {
        val comparison = QueryUnderstandingPipeline.understand("变压器和断路器有啥差别")
        assertEquals(QueryIntent.COMPARISON, comparison.intent)
        assertEquals("变压器和断路器有什么区别", comparison.normalizedQuery)

        val procedure = QueryUnderstandingPipeline.understand("试验数据咋分析")
        assertEquals(QueryIntent.PROCEDURE, procedure.intent)
        assertEquals("试验数据怎么分析", procedure.normalizedQuery)

        val notTriggered = QueryUnderstandingPipeline.understand("线路换熔丝")
        assertEquals("线路换熔丝", notTriggered.normalizedQuery)

        val forbiddenByDomain = QueryUnderstandingPipeline.understand("变压器换熔丝分析")
        assertEquals("变压器换熔丝分析", forbiddenByDomain.normalizedQuery)

        val comparisonForbiddenByDomain = QueryUnderstandingPipeline.understand("变压器和断路器有啥差别分析")
        assertEquals(QueryIntent.COMPARISON, comparisonForbiddenByDomain.intent)
        assertEquals("变压器和断路器有啥差别分析", comparisonForbiddenByDomain.normalizedQuery)

        val comparisonVariantForbiddenByDomain = QueryUnderstandingPipeline.understand("变压器和断路器有啥不一样分析")
        assertEquals(QueryIntent.COMPARISON, comparisonVariantForbiddenByDomain.intent)
        assertEquals("变压器和断路器有啥不一样分析", comparisonVariantForbiddenByDomain.normalizedQuery)
    }
}