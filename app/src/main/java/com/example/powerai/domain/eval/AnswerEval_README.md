Answer-level Evaluation

This module provides simple automatic metrics for generated answers:

- `bleuScore(candidate, references)`: modified BLEU (up to 4-grams) with brevity penalty.
- `rougeLScore(candidate, references)`: ROUGE-L F-measure based on LCS at token level.
- `LlmFactualityScorer`: a best-effort wrapper around the configured AI model (`AiApiService`) that asks the model to rate factuality (0.0-1.0). This requires `BuildConfig.DEEPSEEK_LOGIC_MODEL` and a working AI endpoint.

Usage example (pseudo):

val eval = evaluateAnswer(candidate, references)
val factuality = llmFactualityScorer.score(candidate, references)

Note: LLM-based scoring is approximate and will incur network cost; use cached results for CI runs or provide a mock `AiApiService` in tests.
