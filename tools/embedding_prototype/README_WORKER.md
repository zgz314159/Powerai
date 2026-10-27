# Embedding Worker (retired)

The Android `EmbeddingWorker` (pending JSON under `filesDir/embeddings/pending` + WorkManager)
was **removed in KI-58 (2026-10-27)**. Production retrieval remains lexical FTS.

- `EmbeddingRepositoryImpl.enqueueForEmbedding` only writes Room `embedding_metadata` (status=pending).
- Debug: `:feature:debug` `EmbeddingTestActivity` still offers JNI/FTS/vector sync smoke checks.
- Local HTTP/CLI prototypes under this directory may still be useful for offline experiments;
  they are no longer wired into the app Worker.

See [KNOWN_ISSUES.md](../../KNOWN_ISSUES.md) KI-58.
