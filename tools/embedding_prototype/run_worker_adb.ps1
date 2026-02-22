# Launch EmbeddingTestActivity on device/emulator and show logs
# Usage: run from project root with adb available in PATH

adb shell am start -n com.example.powerai/.ui.test.EmbeddingTestActivity
Write-Host "Started EmbeddingTestActivity"

Write-Host "Tailing logcat for EmbeddingWorker tag (press Ctrl+C to stop)"
adb logcat EmbeddingWorker:V *:S

# To view metrics file (run separately if desired):
# adb shell run-as com.example.powerai cat files/embeddings/embedding_worker_metrics.log
