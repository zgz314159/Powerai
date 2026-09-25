package com.example.powerai.domain.eval

import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.domain.usecase.LocalEvidenceRefiner
import com.example.powerai.domain.usecase.LocalSearchQueryVariantBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRetrievalEvaluationTest {

    private val corpus = listOf(
        KnowledgeItem(
            id = 158L,
            title = "158.隔离开关的主要用途是什么？",
            content = "答：一是检修与分段隔离，二是倒换母线，三是分、合空载电路。",
            source = "铁路电力线路工岗位学标考标必知必会手册",
            pageNumber = null,
            category = "qa",
            keywords = emptyList()
        ),
        KnowledgeItem(
            id = 30L,
            title = "第三十条 运行变压器有下列情况之一时，应立即停止运行：",
            content = "运行变压器有下列情况之一时，应立即停止运行。",
            source = "高速铁路电力管理规则",
            pageNumber = null,
            category = "rule",
            keywords = emptyList()
        ),
        KnowledgeItem(
            id = 63L,
            title = "第63条 更换变压器高压侧熔丝时",
            content = "更换变压器高压侧熔丝时，应先切断低压负荷。",
            source = "高速铁路电力管理规则",
            pageNumber = null,
            category = "rule",
            keywords = emptyList()
        ),
        KnowledgeItem(
            id = 201L,
            title = "第60条 停电操作必须按照断路器、负荷侧隔离开关、电源侧隔离开关顺序操作",
            content = "送电操作顺序与此相反。",
            source = "高速铁路电力管理规则",
            pageNumber = null,
            category = "rule",
            keywords = emptyList()
        ),
        KnowledgeItem(
            id = 128L,
            title = "128.何谓跨步电压？",
            content = "答：电气设备发生接地故障时，人站在接地短路回路上，两脚间就受到地面上不同点之间的电位差，称为跨步电压。",
            source = "电力线路工必知必会手册",
            pageNumber = null,
            category = "qa",
            keywords = emptyList()
        ),
        KnowledgeItem(
            id = 320L,
            title = "如何分析试验数据？",
            content = "答：试验数据应与该设备历次试验、同类设备试验数据进行横向、纵向比较，分析变化规律和趋势后做出结论性判断。",
            source = "牵引变电设备值守人员值班员检修试验岗位必知必会手册",
            pageNumber = null,
            category = "qa",
            keywords = emptyList()
        ),
        KnowledgeItem(
            id = 511L,
            title = "如何分析变压器换熔丝问题？",
            content = "答：变压器换熔丝分析应先核对故障现象、负荷状态、保护动作与熔丝规格，再结合更换前后记录判断原因。",
            source = "变配电检修分析手册",
            pageNumber = null,
            category = "qa",
            keywords = emptyList()
        ),
        KnowledgeItem(
            id = 410L,
            title = "变压器与断路器的区别是什么？",
            content = "答：变压器主要用于变换电压和传输电能，断路器主要用于接通、分断和保护电路。",
            source = "专业知识对比题库",
            pageNumber = null,
            category = "qa",
            keywords = emptyList()
        ),
        KnowledgeItem(
            id = 612L,
            title = "变压器和断路器有啥差别分析 有啥不一样分析 要点",
            content = "答：对变压器和断路器差别进行分析时，应分别从功能定位、保护对象、运行方式和典型故障场景四个维度展开；遇到有啥不一样分析这类问法也按同一差异分析路径处理。",
            source = "设备差异分析题库",
            pageNumber = null,
            category = "qa",
            keywords = emptyList()
        )
    )

    @Test
    fun `local retrieval eval core cases keep top3 recall high`() {
        val cases = LocalRetrievalEvalDataset.coreCases()

        val evaluator = Evaluator(::retrieve)
        val detailed = evaluator.evaluateCases(cases, limit = 3)
        val report = EvalTestSummaryFormatter.formatEdgeCaseReport(
            detailed,
            groupByLabel = true,
            highlightMisses = true
        )

        assertTrue(report, detailed.metrics.recallAtK[3] ?: 0.0 >= 0.99)
        assertTrue(detailed.metrics.mrr >= 0.72)
        assertTrue(report, detailed.cases.none { !it.hitAt3 })
        assertTrue(detailed.cases.first { it.query == "变压器换熔丝分析" }.hitAt3)
        assertTrue((detailed.byLabel["definition"]?.recallAtK?.get(3) ?: 0.0) >= 0.99)
        assertTrue((detailed.byLabel["procedure"]?.recallAtK?.get(3) ?: 0.0) >= 0.99)
        assertTrue((detailed.byLabel["comparison"]?.recallAtK?.get(3) ?: 0.0) >= 0.99)
        assertTrue(EvalTestSummaryFormatter.hasNoMisses(report))
    }

    @Test
    fun `local retrieval eval comparison mixed-domain edge cases stay observable`() {
        val cases = LocalRetrievalEvalDataset.comparisonMixedDomainEdgeCases()

        val evaluator = Evaluator(::retrieve)
        val detailed = evaluator.evaluateCases(cases, limit = 3)
        val report = EvalTestSummaryFormatter.formatEdgeCaseReport(
            detailed,
            groupByLabel = true,
            highlightMisses = true
        )
        val missSummary = EvalTestSummaryFormatter.formatEdgeCaseSummary(
            detailed,
            groupByLabel = true,
            highlightMisses = true,
            missesOnly = true
        )
        val hasMisses = detailed.cases.any { !it.hitAt3 }

        assertEquals(cases.size, detailed.cases.size)
        assertTrue(missSummary, detailed.cases.all { it.retrievedIds.isNotEmpty() })
        assertTrue(EvalTestSummaryFormatter.containsAllLabel(report, "comparison_edge"))
        assertTrue(EvalTestSummaryFormatter.containsMissesSection(report))
        assertTrue(detailed.cases.any { it.query == "变压器和断路器有啥差别分析" })
        assertTrue(detailed.cases.any { it.query == "变压器和断路器有啥不一样分析" })
        assertTrue(EvalTestSummaryFormatter.containsCase(report, "变压器和断路器有啥差别分析"))
        assertTrue(EvalTestSummaryFormatter.containsCase(report, "变压器和断路器有啥不一样分析"))
        assertTrue(EvalTestSummaryFormatter.containsRetrievedList(report))
        if (hasMisses) {
            assertTrue(missSummary.contains("[MISS]"))
        } else {
            assertTrue(EvalTestSummaryFormatter.isNoMissesSummary(missSummary))
        }
    }

    private suspend fun retrieve(query: String, limit: Int): List<KnowledgeItem> {
        val plan = LocalSearchQueryVariantBuilder.buildPlan(query)
        val candidates = corpus.mapNotNull { item ->
            val haystack = "${item.title} ${item.content}".replace("？", " ").replace("。", " ")
            val compactHaystack = haystack.replace(" ", "")
            val hitCount = plan.count { variant ->
                val compact = variant.text.replace(" ", "")
                val parts = variant.text.split(' ').filter { it.length >= 2 }
                compact.isNotBlank() && (
                    compactHaystack.contains(compact) ||
                        (parts.isNotEmpty() && parts.all { compactHaystack.contains(it.replace(" ", "")) })
                    )
            }
            if (hitCount == 0) return@mapNotNull null
            val score = (0.25f + hitCount * 0.12f).coerceAtMost(0.95f)
            RetrievalResult(
                id = item.id,
                score = score,
                confidence = score,
                source = "fts",
                item = item,
                debug = mapOf("query_variant_hit_count" to hitCount)
            )
        }

        return LocalEvidenceRefiner.refine(query, candidates, displayLimit = limit).retrievals.mapNotNull { it.item }
    }
}