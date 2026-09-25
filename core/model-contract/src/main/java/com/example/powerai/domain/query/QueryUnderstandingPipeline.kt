package com.example.powerai.domain.query

object QueryUnderstandingPipeline {
    fun understand(query: String): QueryUnderstandingResult {
        val classificationNormalized = QueryRewritePlanner.normalizeQuery(query)
        val intent = QueryIntentClassifier.classify(classificationNormalized)
        val normalized = QueryRewritePlanner.normalizeQuery(classificationNormalized, intent)
        val retrievalQueries = QueryRewritePlanner.buildRetrievalQueries(normalized)
        val signals = buildSet {
            add(intent.name.lowercase())
            if (QueryRewritePlanner.isConditionStyleQuery(normalized)) {
                add("condition_style")
            }
            if (QueryRewritePlanner.isActionRuleStyleQuery(normalized)) {
                add("action_rule_style")
            }
            if (QueryRewritePlanner.isDefinitionStyleQuery(normalized)) {
                add("definition_style")
            }
            if (retrievalQueries.size > 1) {
                add("rewritten")
            }
        }

        return QueryUnderstandingResult(
            originalQuery = query,
            normalizedQuery = normalized,
            intent = intent,
            retrievalQueries = retrievalQueries,
            signals = signals
        )
    }
}
