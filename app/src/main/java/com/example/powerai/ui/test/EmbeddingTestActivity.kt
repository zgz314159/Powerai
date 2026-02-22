package com.example.powerai.ui.test

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.app.Activity
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.powerai.R
import java.io.File

/**
 * Simple test Activity to create pending embedding JSON files and enqueue the EmbeddingWorker.
 * Launch this Activity on a device/emulator to exercise the embedding pipeline end-to-end.
 */
class EmbeddingTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_embedding_test)

        val createBtn = findViewById<Button>(R.id.btn_create_pending)
        val runBtn = findViewById<Button>(R.id.btn_run_worker)

        createBtn.setOnClickListener {
            val base = File(filesDir, "embeddings")
            val pending = File(base, "pending")
            pending.mkdirs()

            val samples = listOf(
                mapOf("id" to "1001", "content" to "变压器 发热 异常 需 停运"),
                mapOf("id" to "1002", "content" to "变压器 短路 故障 原因 分析")
            )
            for (s in samples) {
                val f = File(pending, "${s["id"]}.json")
                if (!f.exists()) {
                    f.writeText("{\"id\":\"${s["id"]}\",\"content\":\"${s["content"]}\"}")
                }
            }
            Toast.makeText(this, "Created pending files: ${pending.absolutePath}", Toast.LENGTH_LONG).show()
        }

        runBtn.setOnClickListener {
            val work = OneTimeWorkRequestBuilder<com.example.powerai.data.worker.EmbeddingWorker>().build()
            WorkManager.getInstance(this)
                .enqueueUniqueWork("powerai_embedding_worker", ExistingWorkPolicy.REPLACE, work)
            Toast.makeText(this, "Enqueued EmbeddingWorker", Toast.LENGTH_SHORT).show()
        }
    }
}
