Embedding Worker / Service README

Overview
- The Android `EmbeddingWorker` reads pending JSON files placed in `filesDir/embeddings/pending/`.
- Pending JSON format: {"id": "<id>", "content": "<text>"}
- Worker can call a local HTTP service (`/embed_batch`) or execute a Python CLI to compute embeddings.

Quickstart (dev machine)
1) Start the local HTTP service (optional, HTTP mode):

```bash
.venv/Scripts/python.exe -m uvicorn tools.embedding_prototype.service:app --host 127.0.0.1 --port 8000
```

2) Or ensure CLI is available (default):
- The CLI script is `tools/embedding_prototype/embed_batch_cli.py` and uses sentence-transformers.
- Ensure `.venv` is activated and `sentence-transformers` is installed.

3) To test end-to-end on your dev machine (without Android):
- Use the simulate script which mimics the Worker behavior (reads pending, POSTs, writes .emb files):

```bash
.venv/Scripts/python.exe tools/embedding_prototype/simulate_worker.py
```

4) To test on device/emulator:
- Install the app on the device/emulator.
- Launch the `EmbeddingTestActivity` (embedding test) from the launcher (label "Embedding Test") or via ADB:

```bash
adb shell am start -n com.example.powerai/.ui.test.EmbeddingTestActivity
```

- In the activity, tap "Create Pending Files" to create sample pending JSON files under the app's `filesDir/embeddings/pending`.
- Tap "Run EmbeddingWorker" to enqueue the worker; monitor logs via `adb logcat`.

Configuration
- SharedPreferences `powerai_prefs` keys:
  - `embedding_service_mode`: "cli" (default) or "http"
  - `embedding_service_url`: HTTP endpoint (default http://127.0.0.1:8000/embed_batch)
  - `embedding_python_path`: path to python executable for CLI mode (default .venv\\Scripts\\python.exe)
  - `embedding_cli_path`: path to embed_batch_cli.py (default tools/embedding_prototype/embed_batch_cli.py)

Metrics & Logs
- The worker writes metrics to `filesDir/embeddings/embedding_worker_metrics.log` with JSON lines containing `ts`, `processed`, `failures`, `pid`.
- Embedding binary files are written as `<id>.emb` (float32 little-endian) and a small `<id>.json` metadata file with status.

Notes
- The service and CLI use `sentence-transformers/all-MiniLM-L6-v2` by default; ensure model artifacts are available or network access is allowed for downloads.
- For production, consider persisting embeddings into a dedicated vector store or database rather than raw files.
