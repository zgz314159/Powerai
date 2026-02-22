RAG Evaluation Runner

This repository includes a lightweight evaluation framework under `app/src/main/java/com/example/powerai/domain/eval`.

Quick run instructions (manual):

1. Create a JSON dataset file `./eval_dataset.json` with the format:
   {
     "query one": ["docId1", "docId2"],
     "another query": ["docId42"]
   }

2. Implement a small Kotlin runner that loads the JSON, provides a retrieval function (for example by calling the app's `RetrievalFusionUseCase` from a test or simple main), and invokes `Evaluator.evaluate`.

3. For CI: add a JVM unit test in `app/src/test` that wires a mock repository or an in-memory DB seed and runs `Evaluator` asserting minimal thresholds (e.g., precision@5 > 0.1).

This file is an advisory guide; I can scaffold a runnable test harness next if you want.
