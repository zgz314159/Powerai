package com.example.powerai.domain.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryRewritePlannerTest {

    @Test
    fun `classify purpose query as fact question`() {
        assertEquals(QueryIntent.FACT_QUESTION, QueryIntentClassifier.classify("隔离开关用途"))
    }

    @Test
    fun `build retrieval queries expands purpose query`() {
        val queries = QueryRewritePlanner.buildRetrievalQueries("隔离开关用途")

        assertTrue(queries.contains("隔离开关用途"))
        assertTrue(queries.any { it.contains("隔离开关 的主要用途是什么") })
        assertTrue(queries.any { it.contains("隔离开关 的作用是什么") })
    }

    @Test
    fun `build retrieval queries rewrites trailing action phrase`() {
        val queries = QueryRewritePlanner.buildRetrievalQueries("发电机检修")

        assertTrue(queries.contains("发电机检修"))
        assertTrue(queries.contains("检修 发电机"))
        assertTrue(queries.contains("检修发电机"))
    }

    @Test
    fun `definition prefix query is recognized`() {
        assertTrue(QueryRewritePlanner.isDefinitionStyleQuery("什么是跨步电压"))
        assertEquals("跨步电压", QueryRewritePlanner.extractDefinitionSubject("什么是跨步电压"))
        assertEquals("定义", QueryRewritePlanner.extractDefinitionCue("什么是跨步电压"))
    }

    @Test
    fun `colloquial definition and purpose queries are recognized`() {
        assertTrue(QueryAliasCatalog.queryAliases.any { it.pattern == "隔开" && it.canonical == "隔离开关" })
        assertTrue(QueryAliasCatalog.queryAliases(QueryAliasGroup.DEVICES).any { it.pattern == "断电器" && it.canonical == "断路器" })
        assertTrue(QueryAliasCatalog.queryAliases(QueryAliasGroup.ACTION_RULES).any { it.pattern == "换熔丝" && it.canonical == "更换熔丝" })
        assertTrue(QueryAliasCatalog.queryAliases(QueryAliasGroup.ACTION_RULES).any { it.trigger.subjectTokens.contains("变压器") })
        assertTrue(QueryAliasCatalog.queryAliases(QueryAliasGroup.ACTION_RULES).any { it.trigger.requiredDomains.contains(QueryAliasSemanticDomain.DEVICE_OPERATION) })
        assertTrue(QueryAliasCatalog.queryAliases(QueryAliasGroup.ACTION_RULES).any { it.trigger.forbiddenDomains.contains(QueryAliasSemanticDomain.PROCEDURE_ANALYSIS) })
        assertTrue(QueryAliasCatalog.queryAliases(QueryAliasGroup.QUESTION_STYLE).any { it.pattern == "有啥差别" && it.canonical == "有什么区别" })
        assertTrue(QueryAliasCatalog.queryAliases(QueryAliasGroup.QUESTION_STYLE).any { it.trigger.forbiddenDomains.contains(QueryAliasSemanticDomain.PROCEDURE_ANALYSIS) })
        assertTrue(QueryAliasCatalog.definitionAliases(DefinitionAliasGroup.PURPOSE).any { it.pattern == "干啥用" && it.cue == "用途" })
        assertTrue(QueryAliasCatalog.queryAliases(QueryAliasGroup.SAFETY_TERMS).any { it.pattern == "跨步压" && it.canonical == "跨步电压" })
        assertTrue(QueryAliasCatalog.queryAliases(QueryAliasGroup.QUESTION_STYLE).all { !it.applyWhenIntentUnknown })

        assertEquals("变压器更换熔丝", QueryRewritePlanner.normalizeQuery("变压器更换熔丝"))
        assertEquals("变压器更换熔丝", QueryRewritePlanner.normalizeQuery("变压器换熔丝"))
        assertEquals("线路换熔丝", QueryRewritePlanner.normalizeQuery("线路换熔丝"))
        assertEquals("变压器换熔丝分析", QueryRewritePlanner.normalizeQuery("变压器换熔丝分析"))
        assertEquals("变压器和断路器有啥差别", QueryRewritePlanner.normalizeQuery("变压器和断路器有啥差别"))
        assertEquals("变压器和断路器有啥差别分析", QueryRewritePlanner.normalizeQuery("变压器和断路器有啥差别分析", QueryIntent.COMPARISON))
        assertEquals("变压器和断路器有啥不一样分析", QueryRewritePlanner.normalizeQuery("变压器和断路器有啥不一样分析", QueryIntent.COMPARISON))
        assertEquals("变压器和断路器有什么区别", QueryRewritePlanner.normalizeQuery("变压器和断路器有啥差别", QueryIntent.COMPARISON))
        assertEquals("试验数据咋分析", QueryRewritePlanner.normalizeQuery("试验数据咋分析"))
        assertEquals("试验数据怎么分析", QueryRewritePlanner.normalizeQuery("试验数据咋分析", QueryIntent.PROCEDURE))

        assertTrue(QueryRewritePlanner.isDefinitionStyleQuery("隔离开关有啥用"))
        assertEquals("隔离开关", QueryRewritePlanner.extractDefinitionSubject("隔离开关有啥用"))
        assertEquals("用途", QueryRewritePlanner.extractDefinitionCue("隔离开关有啥用"))

        assertTrue(QueryRewritePlanner.isDefinitionStyleQuery("隔开有啥用"))
        assertEquals("隔离开关", QueryRewritePlanner.extractDefinitionSubject("隔开有啥用"))
        assertEquals("用途", QueryRewritePlanner.extractDefinitionCue("隔开有啥用"))

        assertTrue(QueryRewritePlanner.isDefinitionStyleQuery("隔离开关干啥用"))
        assertEquals("隔离开关", QueryRewritePlanner.extractDefinitionSubject("隔离开关干啥用"))
        assertEquals("用途", QueryRewritePlanner.extractDefinitionCue("隔离开关干啥用"))

        assertTrue(QueryRewritePlanner.isDefinitionStyleQuery("电容用途"))
        assertEquals("电容器用途", QueryRewritePlanner.normalizeQuery("电容用途"))
        assertEquals("电容器", QueryRewritePlanner.extractDefinitionSubject("电容用途"))
        assertEquals("用途", QueryRewritePlanner.extractDefinitionCue("电容用途"))

        assertTrue(QueryRewritePlanner.isDefinitionStyleQuery("跨步电压是什么意思"))
        assertEquals("跨步电压", QueryRewritePlanner.extractDefinitionSubject("跨步电压是什么意思"))
        assertEquals("定义", QueryRewritePlanner.extractDefinitionCue("跨步电压是什么意思"))

        assertTrue(QueryRewritePlanner.isDefinitionStyleQuery("啥是跨步电压"))
        assertEquals("跨步电压", QueryRewritePlanner.extractDefinitionSubject("啥是跨步电压"))
        assertEquals("定义", QueryRewritePlanner.extractDefinitionCue("啥是跨步电压"))

        assertTrue(QueryRewritePlanner.isDefinitionStyleQuery("跨步压是啥意思"))
        assertEquals("跨步电压", QueryRewritePlanner.extractDefinitionSubject("跨步压是啥意思"))
        assertEquals("定义", QueryRewritePlanner.extractDefinitionCue("跨步压是啥意思"))
    }
}