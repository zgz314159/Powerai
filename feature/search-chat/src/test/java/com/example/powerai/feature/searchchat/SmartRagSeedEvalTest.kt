package com.example.powerai.feature.searchchat

import com.example.powerai.core.model.KnowledgeItem
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5：消费 `smart_rag_eval/v0_seed_cases.json` 的决策层验收（非 holdout）。
 * 使用 Gson（避免 android.jar org.json stub 在 unitTests 下返回 null）。
 */
class SmartRagSeedEvalTest {
    @Test
    fun `non-holdout seed cases satisfy allowed decisions and claim guards`() {
        val root = loadSeed()
        val failures = mutableListOf<String>()
        for (c in root.cases) {
            if (c.holdout) continue
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
            c.mustRetainFacts.orEmpty().forEach { fact ->
                if (answer.isNotBlank() && !answer.contains(fact)) {
                    failures += "$caseId: missing fact \"$fact\" in answer"
                }
            }
            c.forbiddenClaims.orEmpty().forEach { claim ->
                if (answer.contains(claim)) {
                    failures += "$caseId: forbidden claim \"$claim\" in decision answer"
                }
                // 仅对可注入的具体改写句做守卫回放（如「两人轮流操作」）；元描述型禁止项跳过
                if (evidence.isNotEmpty() && claim.contains("轮流")) {
                    val rewritten =
                        SmartAnswerGuard.coalesceGroundedAnswer(
                            raw = "根据资料，$claim。",
                            references = evidence,
                            question = question,
                        )
                    if (rewritten.contains(claim)) {
                        failures += "$caseId: guard failed to strip claim \"$claim\""
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun evidenceFor(c: SeedCase): List<KnowledgeItem> {
        val fixture = c.fixtureEvidence
        if (fixture != null) {
            return listOf(
                KnowledgeItem(
                    id = 9000L,
                    title = fixture.title,
                    content = fixture.content,
                    source = "eval-fixture",
                    category = "安规",
                    keywords = emptyList(),
                ),
            )
        }
        return when (c.caseId) {
            "SCRN-TD-CZ-001" ->
                listOf(
                    KnowledgeItem(
                        id = 34L,
                        title = "第34条",
                        content =
                            "第34条 停电、检电、接地封线工作必须由二人进行,（一人操作,一人监护）。操作人员应戴绝缘手套。",
                        source = "铁运安规",
                        category = "安规",
                        keywords = emptyList(),
                    ),
                )
            "SCRN-10KV-JX-001" ->
                listOf(
                    KnowledgeItem(
                        id = 10L,
                        title = "10kV接地",
                        content =
                            "10kV设备检修前应拉开开关并接地。复测。确认无电后方可作业。",
                        source = "铁运安规",
                        category = "安规",
                        keywords = emptyList(),
                    ),
                )
            "SHORT-FACT-001" ->
                listOf(
                    KnowledgeItem(
                        id = 11L,
                        title = "接地电阻",
                        content = "本地条款内容关于接地电阻",
                        source = "local.pdf",
                        category = "安规",
                        keywords = emptyList(),
                    ),
                )
            else -> emptyList()
        }
    }

    private fun loadSeed(): SeedRoot {
        val stream =
            requireNotNull(
                javaClass.classLoader!!.getResourceAsStream("smart_rag_eval/v0_seed_cases.json"),
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
    )

    private data class FixtureEvidence(
        val title: String,
        val content: String,
    )
}
