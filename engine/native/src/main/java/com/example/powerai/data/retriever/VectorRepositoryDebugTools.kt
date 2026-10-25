package com.example.powerai.data.retriever

import com.example.powerai.engine.nativecore.NativeVectorRepository

import java.io.File

/**
 * 调试与基准测试工用于开发阶段测试向量存储库.
 * 
 * 这些方法**不应在生产代码中调用**,仅用
 * - 手动测试 (EmbeddingTestActivity)
 * - 性能基准测试
 * - 开发调
 *
 * NativeVectorRepository 拆分出来以保持生产代码职责清
 */
object VectorRepositoryDebugTools {
    private fun flattenVecs(vecs: List<FloatArray>, dim: Int): FloatArray {
        val out = FloatArray(vecs.size * dim)
        vecs.forEachIndexed { i, v -> System.arraycopy(v, 0, out, i * dim, minOf(v.size, dim)) }
        return out
    }


    /**
     * 从目录批量导入嵌入向量文
     * 
     * 每个文件应为 float32 二进制向无头,文件不含扩展应为 Long 类型ID.
     * 不符合规范的文件会被跳过.
     *
     * @param repository 目标向量存储
     * @param embDir 嵌入向量文件目录
     * @param dim 向量维度
     * @param batchSize 批量写入大小
     * @return 成功返回 true, 失败返回 false
     */
    fun ingestEmbeddingsFromDir(
        repository: NativeVectorRepository,
        embDir: File,
        dim: Int,
        batchSize: Int = 500
    ): Boolean {
        return try {
            val expectedBytes = dim * 4L
            val files = embDir.listFiles()?.sortedBy { it.name } ?: return false
            
            var total = 0
            val batchIds = mutableListOf<Long>()
            val batchVecs = mutableListOf<FloatArray>()
            
            fun flush() {
                if (batchIds.isEmpty()) return
                val entries = batchIds.indices.map { i -> batchIds[i] to batchVecs[i] }
                repository.upsert(entries.map { it.first }.toLongArray(), flattenVecs(entries.map { it.second }, dim))
                total += batchIds.size
                batchIds.clear()
                batchVecs.clear()
            }

            for (f in files) {
                val id = f.nameWithoutExtension.toLongOrNull() ?: continue
                if (f.length() != expectedBytes) continue
                
                try {
                    val bytes = f.readBytes()
                    val fb = java.nio.ByteBuffer.wrap(bytes).asFloatBuffer()
                    val arr = FloatArray(fb.limit())
                    fb.get(arr)
                    batchIds.add(id)
                    batchVecs.add(arr)
                } catch (_: Throwable) {
                    // 忽略格式错误的文
                }
                
                if (batchIds.size >= batchSize) flush()
            }
            
            flush()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 执行 NEON 指令集基准测
     * 
     * 使用不同数据集大小测试原生搜索器的性能,返回 (数据集大 耗时ms, 吞吐 三元组列
     *
     * @param dim 向量维度
     * @return 基准测试结果列表 (n, durationMs, throughput)
     */
    fun runNeonBenchmark(dim: Int): List<Triple<Int, Double, Double>> {
        val searcher = NativeAnnSearcher.getInstance(dim)
        val sizes = listOf(100, 500, 1000, 5000)
        val results = mutableListOf<Triple<Int, Double, Double>>()
        
        for (n in sizes) {
            val ids = LongArray(n)
            val vectors = FloatArray(dim * n)
            
            // 生成测试数据
            for (i in 0 until n) {
                ids[i] = 10000L + i
                for (d in 0 until dim) {
                    vectors[i * dim + d] = (i % 7).toFloat() * 0.001f + (d % 5) * 0.0001f
                }
            }
            
            searcher.nativeAddVectors(ids, vectors, dim)
            
            // 预热
            val query = FloatArray(dim)
            query[0] = 1.0f
            searcher.nativeSearch(query, 10)
            
            // 测量
            val t0 = System.nanoTime()
            searcher.nativeSearch(query, 10)
            val durationMs = (System.nanoTime() - t0) / 1e6
            val throughput = n / durationMs
            
            results.add(Triple(n, durationMs, throughput))
        }
        
        return results
    }
}
