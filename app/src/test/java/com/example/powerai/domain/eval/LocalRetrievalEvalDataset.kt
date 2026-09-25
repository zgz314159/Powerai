package com.example.powerai.domain.eval

internal object LocalRetrievalEvalDataset {
    fun coreCases(): List<EvalCase> = listOf(
        EvalCase(query = "隔离开关用途", relevantIds = setOf("158"), label = "definition"),
        EvalCase(query = "隔离开关的作用是什么", relevantIds = setOf("158"), label = "definition"),
        EvalCase(query = "隔开有啥用", relevantIds = setOf("158"), label = "definition"),
        EvalCase(query = "什么是跨步电压", relevantIds = setOf("128"), label = "definition"),
        EvalCase(query = "隔离开关有啥用", relevantIds = setOf("158"), label = "definition"),
        EvalCase(query = "隔离开关干啥用", relevantIds = setOf("158"), label = "definition"),
        EvalCase(query = "跨步电压是什么意思", relevantIds = setOf("128"), label = "definition"),
        EvalCase(query = "啥是跨步电压", relevantIds = setOf("128"), label = "definition"),
        EvalCase(query = "跨步压是啥意思", relevantIds = setOf("128"), label = "definition"),
        EvalCase(query = "变压器什么情况下停止运行", relevantIds = setOf("30"), label = "condition"),
        EvalCase(query = "变压器停运条件", relevantIds = setOf("30"), label = "condition"),
        EvalCase(query = "变压器啥情况停运", relevantIds = setOf("30"), label = "condition"),
        EvalCase(query = "变压器停运啥条件", relevantIds = setOf("30"), label = "condition"),
        EvalCase(query = "变压器更换熔丝", relevantIds = setOf("63"), label = "action"),
        EvalCase(query = "变压器换熔丝", relevantIds = setOf("63"), label = "action"),
        EvalCase(query = "变压器换熔丝分析", relevantIds = setOf("511"), label = "procedure"),
        EvalCase(query = "变压器换熔丝怎么分析", relevantIds = setOf("511"), label = "procedure"),
        EvalCase(query = "如何分析试验数据", relevantIds = setOf("320"), label = "procedure"),
        EvalCase(query = "分析试验数据步骤", relevantIds = setOf("320"), label = "procedure"),
        EvalCase(query = "试验数据怎么分析", relevantIds = setOf("320"), label = "procedure"),
        EvalCase(query = "试验数据咋分析", relevantIds = setOf("320"), label = "procedure"),
        EvalCase(query = "变压器和断路器的区别", relevantIds = setOf("410"), label = "comparison"),
        EvalCase(query = "变压器与断路器不同点", relevantIds = setOf("410"), label = "comparison"),
        EvalCase(query = "变压器跟断路器有啥区别", relevantIds = setOf("410"), label = "comparison"),
        EvalCase(query = "变压器和断路器有啥差别", relevantIds = setOf("410"), label = "comparison"),
        EvalCase(query = "变压器和断路器有啥不一样", relevantIds = setOf("410"), label = "comparison"),
        EvalCase(query = "变压器和断电器有啥不一样", relevantIds = setOf("410"), label = "comparison")
    )

    fun comparisonMixedDomainEdgeCases(): List<EvalCase> = listOf(
        EvalCase(query = "变压器和断路器有啥差别分析", relevantIds = setOf("612"), label = "comparison_edge"),
        EvalCase(query = "变压器和断路器有啥不一样分析", relevantIds = setOf("612"), label = "comparison_edge")
    )
}