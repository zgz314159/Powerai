package com.example.powerai.importer

import com.example.powerai.data.importer.StreamingJsonResourceImporter
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class StreamingImporterBenchmarkTest {
    @Test
    fun runParseDryOnAssetsKb() {
        val base = File("app/src/main/assets/kb")
        if (!base.exists() || !base.isDirectory) {
            println("Assets folder not found: ${base.absolutePath}")
            return
        }

        val files = base.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray()
        if (files.isEmpty()) {
            println("No JSON files found in ${base.absolutePath}")
            return
        }

        for (f in files) {
            println("Benchmarking file: ${f.absolutePath}")
            FileInputStream(f).use { fis ->
                val result = StreamingJsonResourceImporter.parseDry(fis, sampleInterval = 1000)
                println(" -> parsed=${result.itemsParsed}, maxMem=${result.maxMemoryBytes}, durMs=${result.durationMs}")
            }
        }
    }
}
