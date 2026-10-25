package com.example.powerai.domain.eval

import kotlin.math.exp
import kotlin.math.ln

data class AnswerEvalResult(
    val bleu: Double,
    val rougeL: Double
)

private fun tokenize(text: String): List<String> {
    return text.trim().split(Regex("\\s+"))
}

private fun ngramCounts(tokens: List<String>, n: Int): Map<String, Int> {
    if (tokens.size < n) return emptyMap()
    val m = mutableMapOf<String, Int>()
    for (i in 0..tokens.size - n) {
        val ng = tokens.subList(i, i + n).joinToString(" ")
        m[ng] = (m[ng] ?: 0) + 1
    }
    return m
}

/** BLEU (modified n-gram precision up to 4) with brevity penalty. */
fun bleuScore(candidate: String, references: List<String>, maxN: Int = 4): Double {
    val candTokens = tokenize(candidate)
    val refTokensList = references.map { tokenize(it) }
    if (candTokens.isEmpty()) return 0.0

    val precisions = mutableListOf<Double>()
    for (n in 1..maxN) {
        val candCounts = ngramCounts(candTokens, n)
        if (candCounts.isEmpty()) { precisions.add(0.0); continue }
        var match = 0
        var total = 0
        for ((ng, cnt) in candCounts) {
            val maxRef = refTokensList.map { ngramCounts(it, n)[ng] ?: 0 }.maxOrNull() ?: 0
            match += minOf(cnt, maxRef)
            total += cnt
        }
        precisions.add(if (total == 0) 0.0 else match.toDouble() / total.toDouble())
    }

    // geometric mean (use log to avoid underflow)
    val smooth = 1e-9
    val logSum = precisions.map { p -> ln(p + smooth) }.sum()
    val geoMean = exp(logSum / maxN.toDouble())

    // brevity penalty
    val c = candTokens.size
    val refLens = refTokensList.map { it.size }
    val r = refLens.minByOrNull { kotlin.math.abs(it - c) } ?: refLens.first()
    val bp = if (c > r) 1.0 else exp(1.0 - r.toDouble() / c.toDouble())
    return (bp * geoMean).coerceIn(0.0, 1.0)
}

/** ROUGE-L (F-measure based on LCS) */
fun rougeLScore(candidate: String, references: List<String>): Double {
    val candTokens = tokenize(candidate)
    if (candTokens.isEmpty()) return 0.0
    fun lcs(a: List<String>, b: List<String>): Int {
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        return dp[0][0]
    }

    var bestF = 0.0
    for (ref in references) {
        val refTokens = tokenize(ref)
        if (refTokens.isEmpty()) continue
        val l = lcs(candTokens, refTokens)
        val prec = l.toDouble() / candTokens.size.toDouble()
        val rec = l.toDouble() / refTokens.size.toDouble()
        val beta = 1.0
        val f = if (prec + rec == 0.0) 0.0 else ((1 + beta * beta) * prec * rec) / (rec + beta * beta * prec)
        if (f > bestF) bestF = f
    }
    return bestF.coerceIn(0.0, 1.0)
}

fun evaluateAnswer(candidate: String, references: List<String>): AnswerEvalResult {
    return AnswerEvalResult(
        bleu = bleuScore(candidate, references),
        rougeL = rougeLScore(candidate, references)
    )
}
