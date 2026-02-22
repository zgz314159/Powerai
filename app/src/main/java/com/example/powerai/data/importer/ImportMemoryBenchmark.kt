package com.example.powerai.data.importer

import android.util.Log
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
                Log.i("ImportMemoryBenchmark", "parsed=${result.itemsParsed}, maxMem=${result.maxMemoryBytes}, durMs=${result.durationMs}")
            }
        } catch (e: Exception) {
            Log.e("ImportMemoryBenchmark", "benchmark failed", e)
        }
    }
}
