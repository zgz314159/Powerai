package com.example.powerai.engine.ai

import java.io.File

/**
 * 目录内多 GGUF 时的优选规则：同档优先 Instruct，量化优先 Q6/Q5，避免盲目取 listFiles[0]。
 */
object GgufModelPicker {
    fun pickPreferred(ggufs: Array<File>): File? {
        if (ggufs.isEmpty()) return null
        return ggufs.maxWithOrNull(compareBy<File> { score(it.name) }.thenBy { it.name.lowercase() })
    }

    internal fun score(fileName: String): Int {
        val n = fileName.lowercase()
        var s = 0
        if ("instruct" in n) s += 100
        if ("q6_k" in n) s += 40
        if ("q5_k" in n) s += 35
        if ("q8_0" in n || Regex("q8[^0-9]").containsMatchIn(n)) s += 30
        if ("q4_k" in n) s += 15
        if ("q3_k" in n || "q2_k" in n) s -= 25
        // Distill 适合 smoke；Instruct 更利于 grounded 指令遵循。
        if ("distill" in n) s -= 15
        if (Regex("7b|8b|14b|32b").containsMatchIn(n)) s -= 50
        if (Regex("3b").containsMatchIn(n)) s += 8
        if (Regex("1\\.5b|1_5b|1b5").containsMatchIn(n)) s += 5
        return s
    }
}
