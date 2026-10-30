package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import com.google.gson.Gson
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5：消费 `smart_rag_eval/v1_fixed_cases.json`。
 * - 非 holdout：调参/回归主集
 * - holdout：单独断言，不因主集调参而被忽略
 */
class SmartRagFixedEvalTest {
    @Test
    fun `v1 non-holdout fixed cases pass decision and claim guards`() {
        assertEval(holdout = false)
    }

    @Test
    fun `v1 holdout cases pass without being used for tuning`() {
        assertEval(holdout = true)
    }

    @Test
    fun `v1 holdout ratio is at least 20 percent`() {
        val root = load()
        val usable = root.cases.filter { it.question.isNotBlank() && it.question != "(reserved)" }
        val holdout = usable.count { it.holdout }
        assertTrue(
            "holdout=$holdout total=${usable.size} ratio=${holdout.toDouble() / usable.size}",
            holdout.toDouble() / usable.size >= 0.20,
        )
    }

    private fun assertEval(holdout: Boolean) {
        val root = load()
        val failures = mutableListOf<String>()
        for (c in root.cases) {
            if (c.holdout != holdout) continue
            val question = c.question
            if (question.isBlank() || question == "(reserved)") continue
            val caseId = c.caseId
            val evidence = evidenceFor(c)
            val confidence =
                if (evidence.isEmpty()) {
                    SmartEvidenceConfidence.INCORRECT
                } else {
                    SmartEvidenceGrader.grade(question, evidence)
                }
            val decision =
                SmartAnswerDecisionEngine.decide(
                    question = question,
                    qualifiedEvidence = evidence,
                    confidence = confidence,
                )
            val allowed = c.allowedDecisions.orEmpty()
            if (allowed.isNotEmpty() && decision.kind.name !in allowed) {
                failures += "$caseId: decision=${decision.kind} not in $allowed"
            }
            val forbidden = c.forbiddenDecisions.orEmpty()
            if (decision.kind.name in forbidden) {
                failures += "$caseId: forbidden decision ${decision.kind}"
            }
            val answer = decision.directAnswer.orEmpty()
            val evidenceText = evidence.joinToString("\n") { "${it.title}\n${it.content}" }
            c.mustRetainFacts.orEmpty().forEach { fact ->
                when {
                    answer.isNotBlank() && !answer.contains(fact) ->
                        failures += "$caseId: missing fact \"$fact\" in decision answer"
                    answer.isBlank() && evidenceText.isNotBlank() && !evidenceText.contains(fact) ->
                        failures += "$caseId: missing fact \"$fact\" in evidence (FULL path)"
                }
            }
            c.forbiddenClaims.orEmpty().forEach { claim ->
                if (answer.contains(claim)) {
                    failures += "$caseId: forbidden claim in decision \"$claim\""
                }
            }
            val bad = c.injectBadAnswer
            if (!bad.isNullOrBlank() && evidence.isNotEmpty()) {
                val fixed =
                    SmartAnswerGuard.coalesceGroundedAnswer(
                        raw = bad,
                        references = evidence,
                        question = question,
                    )
                c.forbiddenClaims.orEmpty().forEach { claim ->
                    if (claim.length >= 2 && fixed.contains(claim)) {
                        failures += "$caseId: injectBadAnswer still contains \"$claim\""
                    }
                }
                c.mustRetainFacts.orEmpty().take(2).forEach { fact ->
                    if (!fixed.contains(fact) && fixed != SmartAnswerGuard.INSUFFICIENT) {
                        failures += "$caseId: injectBadAnswer fallback missing \"$fact\""
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun evidenceFor(c: SeedCase): List<KnowledgeItem> {
        val fixture = c.fixtureEvidence ?: return emptyList()
        return listOf(
            KnowledgeItem(
                id = 8000L + c.caseId.hashCode().toLong().and(0xffff),
                title = fixture.title,
                content = fixture.content,
                source = "eval-fixture",
                category = "安规",
                keywords = emptyList(),
            ),
        )
    }

    private fun load(): SeedRoot {
        val stream =
            requireNotNull(
                javaClass.classLoader!!.getResourceAsStream("smart_rag_eval/v1_fixed_cases.json"),
            )
        return stream.bufferedReader().use { Gson().fromJson(it, SeedRoot::class.java) }
    }

    private data class SeedRoot(
        val version: String? = null,
        val cases: List<SeedCase> = emptyList(),
    )

    private data class SeedCase(
        val caseId: String,
        val holdout: Boolean = false,
        val question: String = "",
        val allowedDecisions: List<String>? = null,
        val forbiddenDecisions: List<String>? = null,
        val mustRetainFacts: List<String>? = null,
        val forbiddenClaims: List<String>? = null,
        val fixtureEvidence: FixtureEvidence? = null,
        val injectBadAnswer: String? = null,
    )

    private data class FixtureEvidence(
        val title: String,
        val content: String,
    )
}
