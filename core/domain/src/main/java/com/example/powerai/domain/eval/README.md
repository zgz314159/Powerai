RAG Evaluation Suite

This package provides a lightweight evaluator for retrieval quality:

- `Evaluator` accepts a dataset mapping `query -> set of relevant ids` and a `retrievalFn(query, limit)` that returns `List<KnowledgeItem>`.
- Metrics computed: precision@k, recall@k, MRR.

Usage (example):

1. Prepare a dataset JSON with entries: { "query": ["id1","id2"] }
2. Provide a retrieval function that returns `KnowledgeItem`s where `id` is stable and comparable to ground truth ids.
3. Call `Evaluator(retrievalFn).evaluate(dataset)` from a small runner or test.

Note: This evaluator focuses on retrieval metrics. Answer-level evaluation (e.g., factuality, hallucination) requires additional label schemas and LLM-based scorers which can be added later.
