package com.example.powerai.data.importer

import com.example.powerai.util.PLog
import java.io.File
import java.io.FileInputStream

object ImportMemoryBenchmark {
    /**
     * Run a dry parse benchmark on the provided JSON file path and log results.
     */
    fun runFileBenchmark(path: String) {
        try {
            val f = File(path)
            FileInputStream(f).use { fis ->
                val result = StreamingJsonResourceImporter.parseDry(fis, sampleInterval = 1000)
                PLog.i("ImportMemoryBenchmark", "parsed=${result.itemsParsed}, maxMem=${result.maxMemoryBytes}, durMs=${result.durationMs}")
            }
        } catch (e: Exception) {
            PLog.e("ImportMemoryBenchmark", "benchmark failed", e)
        }
    }
}
