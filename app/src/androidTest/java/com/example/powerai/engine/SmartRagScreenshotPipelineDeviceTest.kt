package com.example.powerai.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.SourceQualification
import com.example.powerai.feature.searchchat.SmartAnswerDecisionEngine
import com.example.powerai.feature.searchchat.SmartAnswerDecisionKind
import com.example.powerai.feature.searchchat.SmartAnswerGuard
import com.example.powerai.feature.searchchat.SmartEvidenceGrader
import com.example.powerai.feature.searchchat.SmartQueryIntent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P5 真机管道：打开生产 `powerai.db`，对截图问句做检索级决策断言（不依赖 UiAutomator）。
 */
@RunWith(AndroidJUnit4::class)
class SmartRagScreenshotPipelineDeviceTest {
    @Test
    fun short_entity_clarifies_without_kb() {
        assertTrue(SmartQueryIntent.isAmbiguousOrTooShort("变压器"))
        val d =
            SmartAnswerDecisionEngine.decide(
                question = "变压器",
                qualifiedEvidence = emptyList(),
                confidence = com.example.powerai.feature.searchchat.SmartEvidenceConfidence.INCORRECT,
            )
        assertEquals(SmartAnswerDecisionKind.CLARIFY, d.kind)
    }

    @Test
    fun outage_ask_against_production_kb_keeps_duty_or_honest_none() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = ctx.getDatabasePath("powerai.db")
        assumeTrue("production powerai.db missing", dbFile.exists() && dbFile.length() > 0)

        val db =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "powerai.db")
                .allowMainThreadQueries()
                .build()
        try {
            val hits =
                runBlocking {
                    db.knowledgeDao().searchByKeyword("停电").take(20)
                }
            val items =
                hits.mapNotNull { e ->
                    KnowledgeItem(
                        id = e.id,
                        title = e.title,
                        content = e.content,
                        source = e.source,
                        category = e.category,
                        keywords = emptyList(),
                    ).takeIf { SourceQualification.isQualifiedForNormativeAnswer(it) }
                }
            val q = "停电怎么操作"
            if (items.isEmpty()) {
                val d =
                    SmartAnswerDecisionEngine.decide(
                        question = q,
                        qualifiedEvidence = emptyList(),
                        confidence = com.example.powerai.feature.searchchat.SmartEvidenceConfidence.INCORRECT,
                    )
                assertEquals(SmartAnswerDecisionKind.NONE, d.kind)
                return
            }
            val conf = SmartEvidenceGrader.grade(q, items)
            val d =
                SmartAnswerDecisionEngine.decide(
                    question = q,
                    qualifiedEvidence = items.take(5),
                    confidence = conf,
                )
            assertTrue(
                "kind=${d.kind}",
                d.kind == SmartAnswerDecisionKind.PARTIAL ||
                    d.kind == SmartAnswerDecisionKind.CLARIFY ||
                    d.kind == SmartAnswerDecisionKind.NONE,
            )
            assertFalse(d.kind == SmartAnswerDecisionKind.FULL)
            val answer = d.directAnswer.orEmpty()
            assertFalse(answer.contains("轮流"))
            if (items.any { it.content.contains("一人操作") }) {
                assertTrue(
                    "duty facts expected when KB has them: $answer",
                    answer.contains("一人操作") || answer == SmartAnswerGuard.INSUFFICIENT,
                )
            }
        } finally {
            db.close()
        }
    }
}
